package it.devim.altspam;

import com.sun.mail.util.MailConnectException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.util.*;
import javax.mail.*;

public class ImapClient {
    
    private Encryptor encryptor;
    private Properties prop;
    private Store store;

    public ImapClient() {
        this.encryptor = new Encryptor();
        this.prop = this.loadConfiguration();
        this.prop.setProperty("mail.imap.ssl.enable", "true");
        this.prop.setProperty("mail.imap.connectiontimeout", "4000");
        this.store = null;
    }
    
    /**
     * Check if the client is connected to server
     * @return true if the client is connected
     */
    public boolean isConnected() {
        return ((this.store != null) && (this.store.isConnected()));
    }    
    
    /**
     * If the client is not already connected try to connect to server
     * @return true if the client is connected
     */
    public boolean tryConnect() {
        if (this.isConnected())
            return true;
        boolean result = false;
        Session session = javax.mail.Session.getInstance(this.prop);
        System.out.println("Try connecting to server");
        try {
            store = session.getStore("imap");
            store.connect(this.prop.getProperty("server"), this.prop.getProperty("username"), this.encryptor.decrypt(this.prop.getProperty("password")));
            result = store.isConnected();
        } catch (Exception e) {
            System.out.println("Error: "+e.getMessage());
        }
        return result;
    }

    public boolean isConfigured() {
        return ((this.prop.getProperty("server") != null) &&
                (this.prop.getProperty("username") != null) &&
                (this.prop.getProperty("password") != null) &&
                (this.prop.getProperty("action") != null));
    }
    
    public Encryptor getEncryptor() {
        return this.encryptor;
    }
   
    public void saveConfiguration(String host, String username, String password, String folder, String action, int time) {
        /* String password must be already encrypted */
        //password = this.encryptor.encrypt(password);
        this.prop = new Properties();
        try {
            String dirPath = new File(ImapClient.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            String path = dirPath + FileSystems.getDefault().getSeparator() + "prop.ini";
            System.out.println("Saving settings to "+path);
            try(OutputStream outputStream = new FileOutputStream(path)){  
                this.prop.setProperty("server", host);
                this.prop.setProperty("username", username);
                this.prop.setProperty("password", password);
                this.prop.setProperty("folder", folder);
                this.prop.setProperty("action", action);
                this.prop.setProperty("timeperiod", String.valueOf(time));
                this.prop.store(outputStream, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        this.prop.setProperty("mail.imap.ssl.enable", "true");
        this.prop.setProperty("mail.imap.connectiontimeout", "4000");
    }

    public String getProperty(String key) {
        return this.prop.getProperty("timeperiod");
    }
    
    public Message[] getMessages(int lastn) {
        return this.getMessages(lastn, this.prop.getProperty("folder", "INBOX"));
    }
    
    public String[] getFolders() {
        return this.prop.getProperty("folder", "INBOX").split(",");
    }
    
    /**
     * Get last n received emails for the folder specified
     * @param lastn number of emails to retrieve
     * @param folder folder to check 
     * @return emails downloaded from server
     */
    public Message[] getMessages(int lastn, String folder) {
        Message[] messages = new Message[0];
        try {
            Folder inbox = store.getFolder(folder);
            inbox.open(Folder.READ_ONLY);
            int tot = inbox.getMessageCount();
            System.out.println("Total messages in remote folder '"+folder+"': "+tot);
            int start = tot-lastn+1;
            if (tot < lastn)
                start = 1;
            messages = inbox.getMessages(start, tot);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return messages;
    }
    
    public boolean doAction(Message[] m, String folder) {
        String action = this.prop.getProperty("action", "move");
        switch (action) {
            case "move":
                return this.moveToJunk(m, folder);
            case "delete":
                return this.deleteMessages(m, folder);
            case "none":
            default:
                return true;
        }
    }
    
    private boolean moveToJunk(Message[] m, String folder) {
        if (m.length == 0)
            return true;
        System.out.println("Moving spam messages from "+folder);
        try {
            Folder sfolder = store.getFolder(folder);
            sfolder.open(Folder.READ_WRITE);
            Folder dfolder = store.getFolder("JUNK");
            if (!dfolder.exists())
                dfolder.create(Folder.HOLDS_MESSAGES);
            sfolder.copyMessages(m, dfolder);
            sfolder.setFlags(m, new Flags(Flags.Flag.DELETED), true);
            sfolder.close(true);
            return true;
        } catch (MessagingException e) {
            return false;
        }
    }

    private boolean deleteMessages(Message[] m, String folder) {
        if (m.length == 0)
            return true;
        System.out.println("Deleting spam messages from "+folder);
        try {
            Folder sfolder = store.getFolder(folder);
            sfolder.open(Folder.READ_WRITE);
            sfolder.setFlags(m, new Flags(Flags.Flag.DELETED), true);
            sfolder.close(true);
            return true;
        } catch (MessagingException e) {
            return false;
        }
    }
    
    public Properties loadConfiguration() {
        Properties properties = new Properties();
        try {
            String dirPath = new File(ImapClient.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            String path = dirPath + FileSystems.getDefault().getSeparator() + "prop.ini";
            System.out.println("Reading settings from "+path);
            FileReader reader = new FileReader(path);
            properties.load(reader);
        } catch (IOException | URISyntaxException e) {
            System.out.println("Failed to read settings");
        }
        return properties;
    }
    
    public void close() {
        try {
            //inbox.close(false);
            if (store.isConnected())
                store.close();
        } catch (MessagingException e) {}
    }
}
