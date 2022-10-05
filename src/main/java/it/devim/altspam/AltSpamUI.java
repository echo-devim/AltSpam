package it.devim.altspam;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFrame;

public class AltSpamUI extends javax.swing.JFrame {

    public ImapClient client;
    public Thread antispamThread;
    public AltSpam altspam;
    public String[] emails;
    public TrayIcon trayIcon;
    final private String SW_VERSION = "1.0.0";
    private int lastn_emails;
    private boolean setup;
    
    /**
     * Creates new form AltSpamUI
     */
    public AltSpamUI() {
        initComponents();
        this.setTitle("AltSpam UI " + SW_VERSION);
        this.client = new ImapClient();
        this.altspam = new AltSpam();
        this.emails = new String[0];
        this.trayIcon = null;
        this.setup = false;
        this.lastn_emails = 10;
        String lastn = this.client.getProperty("lastn_emails");
        if (lastn != null) {
            try {
                this.lastn_emails = Integer.parseInt(lastn);
            } catch (Exception e) {}
        }
        if (this.lastn_emails < 5) this.lastn_emails = 10;
        System.out.println("Number of latest emails to check: "+this.lastn_emails);
        // First of all check that the client is configured, otherwise start setup procedure
        if (!this.client.isConfigured()) {
            showSettings(true);
            return;
        }
        this.runAntiSpam();
    }
    
    public ImapClient getClient() {
        return this.client;
    }
    
    public boolean setupSystemTray() {
        if (!SystemTray.isSupported()) {
            System.out.println("System Tray not supported. Showing main window.");
            return false;
        }
        Image icon = null;
        
        try {
            String imagePath = new File(AltSpamUI.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent() + "/icon.png";
            System.out.println("Load icon "+imagePath);
            icon = new ImageIcon(imagePath, "AltSpam tray icon").getImage();
        } catch (URISyntaxException e) {
            System.out.println("Icon not found");
            return false;
        }
        
        final SystemTray tray = SystemTray.getSystemTray();
        Dimension trayIconSize = tray.getTrayIconSize();
        icon = icon.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_SMOOTH);
        this.trayIcon = new TrayIcon(icon);
        // Create popup menu
        PopupMenu popup = new PopupMenu();
        MenuItem exit = new MenuItem("Exit");
 
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Do some cleanup
                tray.remove(trayIcon);
                System.exit(0);
            }
        });
 
        popup.add(exit);
        trayIcon.setToolTip("AltSpam, right click here");
        trayIcon.setPopupMenu(popup);
        
        AltSpamUI aui = this;
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    aui.setVisible(true);
                }
            }
        });
 
        // Add the trayIcon to system tray/notification area
 
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("Could not load tray icon !");
            return false;
        }

        return true;
    }

    /**
     * Function called back by Settings on first run of the application
    */
    public void setup() {
       System.out.println("First run of the application, starting setup");
       this.setup = true;
       this.lastn_emails = 50;
       jLabel1.setText("The systems needs to be trained. Select spam messages from your mailbox and press 'add to spam'. Then, restart the application.");
       this.trayIcon.displayMessage("Info", "Click here to open the window", TrayIcon.MessageType.INFO);
       runAntiSpam();
    }
    
    private void showSettings(boolean firstRun) {
        Settings settings = new Settings(this, firstRun);
        settings.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        settings.setAlwaysOnTop(true);
        settings.setVisible(true);
    }
    
    public void runAntiSpam() {
        JFrame frm = this;
        this.antispamThread = new Thread(new Runnable() {
            public void run() {
                runAntiSpamThread(frm);
            }
        });
        this.antispamThread.start();
    }
    
    
    public void runAntiSpamThread(JFrame frame) {
        while(true) {
            System.out.println("AntiSpam Thread is running");
            if (this.client.tryConnect()) {
                System.out.println("Client connected to server");
                try {
                    //Cleanup the panel
                    jPanel1.removeAll();
                    //Now add the emails
                    String[] folders = this.client.getFolders();
                    this.emails = new String[folders.length*lastn_emails];
                    int folder_index = 0;
                    int pX = 0, pY = 0, total_spam_count = 0;
                    //Iterate over inbox folders to check
                    for (String folder : folders) {
                        //For each folder analyze the last n messages
                        Message[] messages = this.client.getMessages(lastn_emails, folder);
                        int[] spam_msg_indices = new int[messages.length];
                        int spam_count = 0;
                        for (int i = messages.length-1; i >= 0; i--) {
                            int abs_i = i+(folder_index*lastn_emails);
                            Message msg = messages[i];
                            //Convert Message into raw format
                            String mail;
                            ByteArrayOutputStream output = new ByteArrayOutputStream();
                            msg.writeTo(output);
                            mail = output.toString();
                            this.emails[abs_i] = mail;
                            boolean isSpam = this.altspam.isSpam(mail);
                            if (isSpam) {
                                spam_msg_indices[spam_count] = i;
                                spam_count++;
                            }
                            JCheckBox cbMsg = new JCheckBox(abs_i + "|" + msg.getFrom()[0] + ": " + msg.getSubject(), false);
                            if (isSpam) {
                                cbMsg.setBackground(Color.yellow);
                                cbMsg.setOpaque(true);
                            }
                            cbMsg.setVisible(true);
                            cbMsg.setToolTipText(getTextFromMessage(msg));
                            cbMsg.setSize(jPanel1.getWidth(), 20);
                            cbMsg.setLocation(new Point(pX, pY));
                            jPanel1.setPreferredSize(new Dimension(jPanel1.getWidth(), pY+100));
                            jPanel1.add(cbMsg);
                            pY += cbMsg.getHeight();
                        }
                        jPanel1.revalidate();
                        jPanel1.repaint();
                        if (spam_count > 0) {
                            total_spam_count += spam_count;
                            if (!this.setup) {
                                Message[] spam = new Message[spam_count];
                                for (int j = 0; j < spam_count; j++)
                                    spam[j] = messages[spam_msg_indices[j]];
                                this.client.doAction(spam, folder);
                            }
                        }
                        folder_index++;
                    }
                    //Now we have finished to analyze each folder
                    if ((total_spam_count > 0) && (this.trayIcon != null)) {
                        this.trayIcon.displayMessage("Info", "Detected "+total_spam_count+" new spam emails", TrayIcon.MessageType.INFO);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                this.trayIcon.displayMessage("Offline", "Check your connection or settings", TrayIcon.MessageType.WARNING);
            }
            // Wait 10 minutes before next run
            try {
                String stime = this.client.getProperty("timeperiod");
                int time = 10; //minutes
                if (stime != null)
                    time = (Integer.parseInt(stime)/*this is an index*/*10)+10;
                System.out.println("Sleeping for "+time+" minutes");
                Thread.sleep(1000*60*time);
            } catch (Exception e) {}
        }
    }
    
    
       private String getTextFromMessage(Message message) throws MessagingException, IOException {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    private String getTextFromMimeMultipart(
            MimeMultipart mimeMultipart)  throws MessagingException, IOException{
        String result = "";
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result = result + "\n" + bodyPart.getContent();
                break; // without break same text appears twice in my tests
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result = result + "\n" + html;
            } else if (bodyPart.getContent() instanceof MimeMultipart){
                result = result + getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
            }
        }
        return result;
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnSettings = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jbtnAddSpam = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        jBtnRemoveSpam = new javax.swing.JButton();
        btnExit = new javax.swing.JButton();

        setTitle("AltSpam UI");
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        btnSettings.setText("Settings");
        btnSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSettingsActionPerformed(evt);
            }
        });

        jLabel1.setText("Last received emails (spam highlighted in yellow):");

        jbtnAddSpam.setText("Add to spam");
        jbtnAddSpam.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbtnAddSpamActionPerformed(evt);
            }
        });

        jScrollPane2.setBackground(new java.awt.Color(153, 153, 255));
        jScrollPane2.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        jScrollPane2.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        jScrollPane2.setAutoscrolls(true);

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1007, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 270, Short.MAX_VALUE)
        );

        jScrollPane2.setViewportView(jPanel1);

        jBtnRemoveSpam.setText("Remove from spam");
        jBtnRemoveSpam.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jBtnRemoveSpamActionPerformed(evt);
            }
        });

        btnExit.setText("Exit");
        btnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExitActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 1019, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.LEADING))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jbtnAddSpam)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jBtnRemoveSpam)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnExit)
                        .addGap(18, 18, 18))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(9, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSettings)
                    .addComponent(jbtnAddSpam)
                    .addComponent(jBtnRemoveSpam)
                    .addComponent(btnExit))
                .addGap(16, 16, 16))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSettingsActionPerformed
        this.showSettings(false);
    }//GEN-LAST:event_btnSettingsActionPerformed

    private void jbtnAddSpamActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbtnAddSpamActionPerformed
        int i = 0;
        for (Component c : jPanel1.getComponents()) {
            JCheckBox chb = (JCheckBox)c;
            if (chb.isSelected()) {
                try {
                    int id = Integer.parseInt(chb.getText().substring(0,chb.getText().indexOf('|')));
                    this.altspam.addToSpam(this.emails[id]);
                    chb.setSelected(false);
                } catch (Exception e) { e.printStackTrace(); }
            }
            i++;
        }
    }//GEN-LAST:event_jbtnAddSpamActionPerformed

    private void jBtnRemoveSpamActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jBtnRemoveSpamActionPerformed
        int i = 0;
        for (Component c : jPanel1.getComponents()) {
            JCheckBox chb = (JCheckBox)c;
            if (chb.isSelected()) {
                try {
                    int id = Integer.parseInt(chb.getText().substring(0,chb.getText().indexOf('|')));
                    this.altspam.removeFromSpam(this.emails[id]);
                    chb.setSelected(false);
                } catch (Exception e) { e.printStackTrace(); }
            }
            i++;
        }
    }//GEN-LAST:event_jBtnRemoveSpamActionPerformed

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        if (this.setupSystemTray()) {
            this.setVisible(false);
        } else {
            this.setVisible(true);
        }
    }//GEN-LAST:event_formWindowOpened

    private void btnExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExitActionPerformed
        System.exit(0);
    }//GEN-LAST:event_btnExitActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(AltSpamUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(AltSpamUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(AltSpamUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(AltSpamUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        
        
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new AltSpamUI().setVisible(true);
            }
        });
        
        
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnExit;
    private javax.swing.JButton btnSettings;
    private javax.swing.JButton jBtnRemoveSpam;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton jbtnAddSpam;
    // End of variables declaration//GEN-END:variables
}
