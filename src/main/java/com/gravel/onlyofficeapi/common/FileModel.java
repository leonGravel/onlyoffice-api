package com.gravel.onlyofficeapi.common;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class FileModel
{
    public String type = "desktop";
    public String documentType;
    public Document document;
    public EditorConfig editorConfig;
    public String token;

    public FileModel(String fileName)
    {
        if (fileName == null) fileName = "";
        fileName = fileName.trim();

        String userId = DocumentManager.CurUserHostAddress(null);
        documentType = FileUtility.GetFileType(fileName).toString().toLowerCase();

        document = new Document();
        document.title = fileName;
        //document.url = DocumentManager.GetFileUri(fileName).replaceAll("localhost", userId);
        document.url = DocumentManager.GetDownback(fileName).replaceAll("localhost", userId);
        document.fileType = FileUtility.GetFileExtension(fileName).replace(".", "");
        document.key = ServiceConverter.GenerateRevisionId(userId + "/" + fileName);

        editorConfig = new EditorConfig();
        if (!DocumentManager.GetEditedExts().contains(FileUtility.GetFileExtension(fileName)))
            editorConfig.mode = "view";
        editorConfig.callbackUrl = DocumentManager.GetCallback(fileName).replaceAll("localhost", userId);
        editorConfig.user.id = userId;
        editorConfig.customization.goback.url = (DocumentManager.GetServerUrl() + "/IndexServlet").replaceAll("localhost", userId);
    }

    public void InitDesktop()
    {
        type = "embedded";
        editorConfig.InitDesktop(document.url);
    }

    public void BuildToken()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("documentType", documentType);
        map.put("document", document);
        map.put("editorConfig", editorConfig);

        token = DocumentManager.CreateToken(map);
    }

    public class Document
    {
        public String title;
        public String url;
        public String fileType;
        public String key;
    }

    public class EditorConfig
    {
        public String mode = "edit";
        public String callbackUrl;
        public User user;
        public Customization customization;
        public Embedded embedded;

        public EditorConfig()
        {
            user = new User();
            customization = new Customization();
        }

        public void InitDesktop(String url)
        {
            embedded = new Embedded();
            embedded.saveUrl = url;
            embedded.embedUrl = url;
            embedded.shareUrl = url;
            embedded.toolbarDocked = "top";
        }

        public class User
        {
            public String id;
            public String name = "John Smith";
        }

        public class Customization
        {
            public Goback goback;

            public Customization()
            {
                goback = new Goback();
            }

            public class Goback
            {
                public String url;
            }
        }

        public class Embedded
        {
            public String saveUrl;
            public String embedUrl;
            public String shareUrl;
            public String toolbarDocked;
        }
    }


    public static String Serialize(FileModel model)
    {
        Gson gson = new Gson();
        return gson.toJson(model);
    }
}
