package com.gravel.onlyofficeapi.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.primeframework.jwt.Signer;
import org.primeframework.jwt.Verifier;
import org.primeframework.jwt.domain.JWT;
import org.primeframework.jwt.hmac.HMACSigner;
import org.primeframework.jwt.hmac.HMACVerifier;



public class DocumentManager
{
    private static HttpServletRequest request;

    public static void Init(HttpServletRequest req, HttpServletResponse resp)
    {
        request = req;
    }

    public static long GetMaxFileSize()
    {
        long size;

        try
        {
            size = Long.parseLong(ConfigManager.GetProperty("filesize-max"));
        }
        catch (Exception ex)
        {
            size = 0;
        }

        return size > 0 ? size : 5 * 1024 * 1024;
    }

    public static List<String> GetFileExts()
    {
        List<String> res = new ArrayList<>();

        res.addAll(GetViewedExts());
        res.addAll(GetEditedExts());
        res.addAll(GetConvertExts());

        return res;
    }

    public static List<String> GetViewedExts()
    {
        String exts = ConfigManager.GetProperty("files.docservice.viewed-docs");
        return Arrays.asList(exts.split("\\|"));
    }

    public static List<String> GetEditedExts()
    {
        String exts = ConfigManager.GetProperty("files.docservice.edited-docs");
        return Arrays.asList(exts.split("\\|"));
    }

    public static List<String> GetConvertExts()
    {
        String exts = ConfigManager.GetProperty("files.docservice.convert-docs");
        return Arrays.asList(exts.split("\\|"));
    }

    public static String CurUserHostAddress(String userAddress)
    {
        if(userAddress == null)
        {
            try
            {
                userAddress = InetAddress.getLocalHost().getHostAddress();
            }
            catch (Exception ex)
            {
                userAddress = "";
            }
        }

        return userAddress.replaceAll("[^0-9a-zA-Z.=]", "_");
    }

    public static String StoragePath(String fileName, String userAddress)
    {
        String storagePath = ConfigManager.GetProperty("storage-folder");
        File file = new File(storagePath);

        if (!file.exists())
        {
            file.mkdir();
        }

        return storagePath + fileName;
    }

    public static String GetCorrectName(String fileName)
    {
        String baseName = FileUtility.GetFileNameWithoutExtension(fileName);
        String ext = FileUtility.GetFileExtension(fileName);
        String name = baseName + ext;

        File file = new File(StoragePath(name, null));

        for (int i = 1; file.exists(); i++)
        {
            name = baseName + "(" + i + ")" + ext;
            file = new File(StoragePath(name, null));
        }

        return name;
    }

    public static String CreateDemo(String fileExt) throws Exception
    {
        String demoName = "sample." + fileExt;
        String fileName = GetCorrectName(demoName);

        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(demoName);

        File file = new File(StoragePath(fileName, null));

        try (FileOutputStream out = new FileOutputStream(file))
        {
            int read;
            final byte[] bytes = new byte[1024];
            while ((read = stream.read(bytes)) != -1)
            {
                out.write(bytes, 0, read);
            }
            out.flush();
        }

        return fileName;
    }

    public static String GetFileUri(String fileName)
    {
        String serverPath = GetServerUrl();
		String storagePath = ConfigManager.GetProperty("storage-folder");
		String hostAddress = CurUserHostAddress(null);

		String filePath = "";
		try {
			//filePath = serverPath + "/" + storagePath + "/" + hostAddress + "/" + URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20");
			filePath = serverPath + "/" + storagePath  + "/" + URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return filePath;
    }

    public static String GetServerUrl()
    {
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
    }

    public static String GetCallback(String fileName)
    {
        String serverPath = GetServerUrl();
        String hostAddress = CurUserHostAddress(null);
        String query="";
		try {
			query = "?type=track&fileName=" + URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()) + "&userAddress=" + URLEncoder.encode(hostAddress, java.nio.charset.StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return serverPath + "/IndexServlet" + query;
    }

    public static String GetDownback(String fileName)
    {
        String serverPath = GetServerUrl();
        String query="";
		query = "?fileName=" +fileName;
		return serverPath + "/downloadServlet" + query;
    }
    
    public static String GetInternalExtension(FileType fileType)
    {
        if (fileType.equals(FileType.Text))
            return ".docx";

        if (fileType.equals(FileType.Spreadsheet))
            return ".xlsx";

        if (fileType.equals(FileType.Presentation))
            return ".pptx";

        return ".docx";
    }

    public static String CreateToken(Map<String, Object> payloadClaims)
    {
        try
        {
            Signer signer = HMACSigner.newSHA256Signer(GetTokenSecret());
            JWT jwt = new JWT();
            for (String key : payloadClaims.keySet())
            {
                jwt.addClaim(key, payloadClaims.get(key));
            }
            return JWT.getEncoder().encode(jwt, signer);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    public static JWT ReadToken(String token)
    {
        try
        {
            Verifier verifier = HMACVerifier.newVerifier(GetTokenSecret());
            return JWT.getDecoder().decode(token, verifier);
        }
        catch (Exception exception)
        {
            return null;
        }
    }

    public static Boolean TokenEnabled()
    {
        String secret = GetTokenSecret();
        return secret != null && !secret.isEmpty();
    }

    private static String GetTokenSecret()
    {
        return ConfigManager.GetProperty("files.docservice.secret");
    }
}