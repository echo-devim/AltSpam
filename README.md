# AltSpam

AltSpam is a lightweight antispam service to keep your mailbox free from spam emails.
The core of this project is the AltSpam library (libAltSpam).
Visit https://github.com/echo-devim/libAltSpam for more details.

The library repo contains a tiny API server implementation written in C++. It is possible to call AltSpam functions through remote procedure calls.
Here you can find a Java application that defines AltSpam class as a client for the API server.
The Java application uses the standard library javax mail to connect to the remote IMAP server, then downloads the emails and passes them to the antispam library to check if the (10 by default) latest emails are good or spam.

When you execute the JAR file, using `$ java -jar altspam.jar`, the Java application launches the AltSpam server. You can manually launch the server before the java client using `$ ./altspam --server` command.
The first time, the application shows a settings form where you must put details about the server and the credentials to use. Then, you must train the application selecting some spam messages from your INBOX or JUNK folder. This step is needed to build the initial spam dataset.
After the initial setup, you can restart the application that will mark spam messages perfoming the action specified.

![altspam screen](https://github.com/echo-devim/AltSpam/raw/master/screen.jpg)

The possible actions are the following:
*  Do nothing (used only for testing purposes)
*  Move the detected spam to junk folder (recommended option)
*  Delete spam messages (I mean, really delete.. pay attention)

You can manually add or remove emails to spam dataset in order to improve the precision of the system. The tests has shown good results.

The application is based on a tray icon and a graphical user interface.

In the application settings you can specify more than one incoming folder to monitor. Set multiple folders split by commas (e.g. "INBOX,INBOX/folder2,JUNK").

## How to run
On Windows just double-click `altspam.jar` package contained in the release. On Linux run the jar file using `$ java -jar altspam.jar`.

## Compilation
Use the `make_release.sh` script in this repo to compile for Linux and/or Windows. Pass `1` as argument to the script to compile for Linux or `2` for Windows.

Example:
```sh
$ bash make_release.sh 2
```

The output will be saved in the `release` directory.