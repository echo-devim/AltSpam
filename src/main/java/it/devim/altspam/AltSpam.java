package it.devim.altspam;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

enum OPERATION {
	LOAD_WORD_COUNT,
	SAVE_WORD_COUNT,
	IMPORT_WORD_COUNT,
	ADD_TO_SPAM,
	REMOVE_FROM_SPAM,
	IS_SPAM,
	SPAM_SCORE,
        RELOAD_WORD_COUNT
};

/* AltSpam Class is a wrapper of a socket to call remote APIs of AltSpam Service */
public class AltSpam {

    private Socket apiclient;
    private Process server;
    final private byte HEADER_SIZE = 5;
    
    public AltSpam() {
        //Try to run the API server process
        try {
            String serverpath = new File(AltSpam.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent()+"/altspam";
            System.out.println("Starting server at "+serverpath);
            this.server = new ProcessBuilder(serverpath,"--server").start();
            Thread.sleep(1000);
        } catch (Exception e) {
            this.server = null;
            System.out.println("Cannot run server");
        }
        this.apiclient = null;
        this.connect();
    }
    
    /**
     * Connect to the API server (12 attempts maximum).
    */
    public void connect() {
        int i = 0;
        while ((this.apiclient == null) || (!this.apiclient.isConnected())) {
            System.out.println("Connecting to AltSpam service");
            try {
                this.apiclient = new Socket("127.0.0.1", 22525);
                return;
            } catch(IOException e) {
                System.out.println("Cannot connect to AltSpam service: " + e.getMessage());
            }
            try {
                Thread.sleep(5000);
            } catch(InterruptedException e) {}
            if (i > 12) {
                System.exit(1);
            }
            i++;
        }
    }
    
    /**
     * Call the remote function on server.
     * Message structure:
     * |OPERATION|SIZE|DATA|
    */
    private String callRemoteFunction(OPERATION op, byte[] payload, int len) {
        this.connect();
        String ret = "";
        try {
            OutputStream output = this.apiclient.getOutputStream();	
            InputStream input = this.apiclient.getInputStream();
            byte[] data = new byte[HEADER_SIZE+len];
            data[0] = (byte)op.ordinal();
            data[1] = (byte)(len >>> 24);
            data[2] = (byte)(len >>> 16);
            data[3] = (byte)(len >>> 8);
            data[4] = (byte)len;
            System.arraycopy(payload, 0, data, HEADER_SIZE, data.length-HEADER_SIZE);
            for (int i = HEADER_SIZE; i < data.length; i++) {
                data[i] = (byte) (data[i] ^ data[4]);
            }
            output.write(data);
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            ret = reader.readLine();
        } catch(IOException e) {
            System.out.println("Connection lost: "+e.getMessage());
        }
        return ret;
    }
    
    public float getSpamScore(String email) {
        return Float.parseFloat(this.callRemoteFunction(OPERATION.SPAM_SCORE, email.getBytes(StandardCharsets.UTF_8), email.length()-1));
    }

    public boolean isSpam(String email) {
        return (Integer.parseInt(this.callRemoteFunction(OPERATION.IS_SPAM, email.getBytes(StandardCharsets.UTF_8), email.length()-1)) == 1);
    }

    public void addToSpam(String email) {
        this.callRemoteFunction(OPERATION.ADD_TO_SPAM, email.getBytes(StandardCharsets.UTF_8), email.length()-1);
    }

    public void removeFromSpam(String email) {
        this.callRemoteFunction(OPERATION.REMOVE_FROM_SPAM, email.getBytes(StandardCharsets.UTF_8), email.length()-1);
    }
    
    public boolean saveWordCount(String path) {
        return (Integer.parseInt(this.callRemoteFunction(OPERATION.SAVE_WORD_COUNT, path.getBytes(StandardCharsets.UTF_8), path.length()-1)) == 1);
    }

    public boolean loadWordCount(String path) {
        return (Integer.parseInt(this.callRemoteFunction(OPERATION.LOAD_WORD_COUNT, path.getBytes(StandardCharsets.UTF_8), path.length()-1)) == 1);
    }
    
    public boolean reloadWordCount() {
        return (Integer.parseInt(this.callRemoteFunction(OPERATION.RELOAD_WORD_COUNT, new byte[1], 1)) == 1);
    }
    
    public boolean importWordCount(String path) {
        return (Integer.parseInt(this.callRemoteFunction(OPERATION.IMPORT_WORD_COUNT, path.getBytes(StandardCharsets.UTF_8), path.length()-1)) == 1);
    }

}
