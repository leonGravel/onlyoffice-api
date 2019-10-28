package com.gravel.onlyofficeapi.controller;

import com.alibaba.fastjson.JSON;
import com.gravel.onlyofficeapi.common.*;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.primeframework.jwt.domain.JWT;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URL;
import java.util.Scanner;

@Slf4j
@Controller
public class FileHandleController {
	
	@RequestMapping("/IndexServlet")
	public String fileIndex(HttpServletRequest request, HttpServletResponse response,Model model) throws Exception{
        String action = request.getParameter("type");
        model.addAttribute("urlPreloader", ConfigManager.GetProperty("files.docservice.url.preloader"));
        model.addAttribute("editedExtList", String.join(",", DocumentManager.GetEditedExts()));
        model.addAttribute("converExtList", String.join(",", DocumentManager.GetConvertExts()));
        if (action == null)
        {
            return "index";
        }
        DocumentManager.Init(request, response);
        response.setCharacterEncoding("utf-8");
        PrintWriter writer = response.getWriter();

        switch (action.toLowerCase())
        {
            case "upload":
                Upload(request, response, writer);
                break;
            case "convert":
                Convert(request, response, writer);
                break;
            case "track":
                Track(request, response, writer);
                break;
        }
		return null;
	}
	
	@RequestMapping("/EditorServlet")
	public String fileEditor(HttpServletRequest request, HttpServletResponse response,Model model) throws Exception{
        DocumentManager.Init(request, response);
        String fileName = request.getParameter("fileName");
        String fileExt = request.getParameter("fileExt");
        if (fileExt != null)
        {
            try
            {
                fileName = DocumentManager.CreateDemo(fileExt);
            }
            catch (Exception ex)
            {
                response.getWriter().write("Error: " + ex.getMessage());    
            }
        }

        FileModel file = new FileModel(fileName);
        if ("embedded".equals(request.getParameter("mode")))
            file.InitDesktop();
        if ("view".equals(request.getParameter("mode")))
            file.editorConfig.mode = "view";

        if (DocumentManager.TokenEnabled())
        {
            file.BuildToken();
        }
        
        JSON config=JSON.parseObject(FileModel.Serialize(file));
        model.addAttribute("config", config);
        model.addAttribute("docserviceApiUrl", ConfigManager.GetProperty("files.docservice.url.api"));
        return "editor";
    }
	
	
	@RequestMapping("/downloadServlet")
	public void fileDownload(HttpServletRequest request, HttpServletResponse response,Model model) throws Exception{
        	String fileName = request.getParameter("fileName");
        	String storagePath = DocumentManager.StoragePath(fileName, null);
    		//以下载方式打开
    		response.setHeader("Content-Disposition", "attachment;filename=\"" +fileName+ "\"");
    		response.setContentType("multipart/form-data");
    		File file = new File(storagePath);
    		FileInputStream fis =  new FileInputStream(file);
    		//写出
    		 ServletOutputStream out = response.getOutputStream();
    		//定义读取缓冲区
    		byte buffer[] = new byte[1024];
    		//定义读取长度
    		int len = 1024;
    		//循环读取
    		while((len = fis.read(buffer))!=-1){
    			out.write(buffer,0,len);
    		}
    		//释放资源
    		fis.close();
    		out.flush();
    		out.close();
    	}	

	
	
	private static void Upload(HttpServletRequest request, HttpServletResponse response, PrintWriter writer)
    {
        response.setContentType("text/plain");

        try
        {
            Part httpPostedFile = request.getPart("file");

            String fileName = "";
            for (String content : httpPostedFile.getHeader("content-disposition").split(";"))
            {
                if (content.trim().startsWith("filename"))
                {
                    fileName = content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
                }
            }

            long curSize = httpPostedFile.getSize();
            if (DocumentManager.GetMaxFileSize() < curSize || curSize <= 0)
            {
                writer.write("{ \"error\": \"File size is incorrect\"}");
                return;
            }

            String curExt = FileUtility.GetFileExtension(fileName);
            if (!DocumentManager.GetFileExts().contains(curExt))
            {
                writer.write("{ \"error\": \"File type is not supported\"}");
                return;
            }

            InputStream fileStream = httpPostedFile.getInputStream();

            fileName = DocumentManager.GetCorrectName(fileName);
            String fileStoragePath = DocumentManager.StoragePath(fileName, null);

            File file = new File(fileStoragePath);

            try (FileOutputStream out = new FileOutputStream(file))
            {
                int read;
                final byte[] bytes = new byte[1024];
                while ((read = fileStream.read(bytes)) != -1)
                {
                    out.write(bytes, 0, read);
                }

                out.flush();
            }

            writer.write("{ \"filename\": \"" + fileName + "\"}");

        }
        catch (IOException | ServletException e)
        {
            writer.write("{ \"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private static void Convert(HttpServletRequest request, HttpServletResponse response, PrintWriter writer)
    {
        response.setContentType("text/plain");

        try
        {
            String fileName = request.getParameter("filename");
            String fileUri = DocumentManager.GetFileUri(fileName);
            String fileExt = FileUtility.GetFileExtension(fileName);
            FileType fileType = FileUtility.GetFileType(fileName);
            String internalFileExt = DocumentManager.GetInternalExtension(fileType);

            if (DocumentManager.GetConvertExts().contains(fileExt))
            {
                String key = ServiceConverter.GenerateRevisionId(fileUri);

                String newFileUri = ServiceConverter.GetConvertedUri(fileUri, fileExt, internalFileExt, key, true);

                if (newFileUri.isEmpty())
                {
                    writer.write("{ \"step\" : \"0\", \"filename\" : \"" + fileName + "\"}");
                    return;
                }

                String correctName = DocumentManager.GetCorrectName(FileUtility.GetFileNameWithoutExtension(fileName) + internalFileExt);

                URL url = new URL(newFileUri);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                InputStream stream = connection.getInputStream();

                if (stream == null)
                {
                    throw new Exception("Stream is null");
                }

                File convertedFile = new File(DocumentManager.StoragePath(correctName, null));
                try (FileOutputStream out = new FileOutputStream(convertedFile))
                {
                    int read;
                    final byte[] bytes = new byte[1024];
                    while ((read = stream.read(bytes)) != -1)
                    {
                        out.write(bytes, 0, read);
                    }

                    out.flush();
                }

                connection.disconnect();

                //remove source file ?
                //File sourceFile = new File(DocumentManager.StoragePath(fileName, null));
                //sourceFile.delete();

                fileName = correctName;
            }

            writer.write("{ \"filename\" : \"" + fileName + "\"}");

        }
        catch (Exception ex)
        {
            writer.write("{ \"error\": \"" + ex.getMessage() + "\"}");
        }
    }

    private static void Track(HttpServletRequest request, HttpServletResponse response, PrintWriter writer)
    {
        String userAddress = request.getParameter("userAddress");
        String fileName = request.getParameter("fileName");

        String storagePath = DocumentManager.StoragePath(fileName, userAddress);
        String body = "";

        try
        {
            Scanner scanner = new Scanner(request.getInputStream());
            scanner.useDelimiter("\\A");
            body = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            log.info("body体内容====:"+body);
        }
        catch (Exception ex)
        {
            writer.write("get request.getInputStream error:" + ex.getMessage());
            return;
        }

        if (body.isEmpty())
        {
            writer.write("empty request.getInputStream");
            return;
        }

        JSONParser parser = new JSONParser();
        JSONObject jsonObj;

        try
        {
            Object obj = parser.parse(body);
            jsonObj = (JSONObject) obj;
        }
        catch (Exception ex)
        {
            writer.write("JSONParser.parse error:" + ex.getMessage());
            return;
        }

        int status;
        String downloadUri;

        if (DocumentManager.TokenEnabled())
        {
            String token = (String) jsonObj.get("token");

            JWT jwt = DocumentManager.ReadToken(token);
            if (jwt == null)
            {
                writer.write("JWT.parse error");
                return;
            }

            status = jwt.getInteger("status");
            downloadUri = jwt.getString("url");
        }
        else
        {
            status =new Integer(jsonObj.get("status").toString());
            downloadUri = (String) jsonObj.get("url");
        }

        int saved = 0;
        if (status == 2 || status == 3)//MustSave, Corrupted
        {
            try
            {
                URL url = new URL(downloadUri);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                InputStream stream = connection.getInputStream();

                if (stream == null)
                {
                    throw new Exception("Stream is null");
                }

                File savedFile = new File(storagePath);
                try (FileOutputStream out = new FileOutputStream(savedFile))
                {
                    int read;
                    final byte[] bytes = new byte[1024];
                    while ((read = stream.read(bytes)) != -1)
                    {
                        out.write(bytes, 0, read);
                    }

                    out.flush();
                }

                connection.disconnect();

            }
            catch (Exception ex)
            {
                saved = 1;
            }
        }

        writer.write("{\"error\":" + saved + "}");
    }
}
