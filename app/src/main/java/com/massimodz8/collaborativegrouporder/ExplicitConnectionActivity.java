package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class ExplicitConnectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explicit_connection);
    }

    private static class Attempt {
        public String addr;
        public int port;

        public Attempt(String addr, int port) {
            this.addr = addr;
            this.port = port;
        }
    }
    public static class Shaken {
        public static class Error {
            String title;
            String msg;
            Integer refocus;
            Error(String title, String msg) { this.title = title; this.msg = msg; }
        }
        public Error ohno; // if this exists the rest is to be ignored
        public Socket socket;
        public ConnectedGroup cg;
    }

    public void startExplicitConnection(View btn) {
        EditText view = (EditText)findViewById(R.id.in_explicit_inetAddr);
        final String host = view.getText().toString();
        view = (EditText)findViewById(R.id.in_explicit_port);
        int port;
        try {
            port = Integer.parseInt(view.getText().toString());
        } catch(NumberFormatException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(R.string.badPort_msg);
            build.show();
            view.requestFocus();
            return;
        }
        final ExplicitConnectionActivity self = this;
        new AsyncTask<Attempt, Void, Shaken>() {
            @Override
            protected Shaken doInBackground(Attempt... params) {
                Socket pipe = null;
                Shaken hello = new Shaken();
                try {
                    pipe = new Socket(params[0].addr, params[0].port);
                    ObjectInputStream reader = new ObjectInputStream(pipe.getInputStream());
                    Object reply = reader.readObject();
                    hello.cg = (ConnectedGroup)reply;
                } catch (UnknownHostException e) {
                    hello.ohno = new Shaken.Error(null, getString(R.string.badHost_msg));
                    hello.ohno.refocus = new Integer(R.id.in_explicit_inetAddr);
                } catch (IOException e) {
                    hello.ohno = new Shaken.Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
                } catch (ClassNotFoundException e) {
                    hello.ohno = new Shaken.Error(null, getString(R.string.badInitialServerReply_msg));
                } catch (ClassCastException e) {
                    hello.ohno = new Shaken.Error(null, getString(R.string.unexpectedInitialServerReply_msg));
                }
                hello.socket = pipe;
                return hello;
            }

            @Override
            protected void onPostExecute(Shaken shaken) {
                if(shaken.ohno != null) {
                    AlertDialog.Builder build = new AlertDialog.Builder(self);
                    if(shaken.ohno.title != null && !shaken.ohno.title.isEmpty()) build.setTitle(shaken.ohno.title);
                    if(shaken.ohno.msg != null && !shaken.ohno.msg.isEmpty()) build.setMessage(shaken.ohno.msg);
                    if(shaken.ohno.refocus != null) findViewById(shaken.ohno.refocus.intValue()).requestFocus();
                    build.show();
                    return;
                }
                AlertDialog.Builder build = new AlertDialog.Builder(self);
                String yes_oh_yes = "";
                yes_oh_yes += "Version is " + shaken.cg.version + '\n';
                yes_oh_yes += "Group name is " + shaken.cg.name + '\n';
                build.setMessage(yes_oh_yes);
                build.show();
            }
        }.execute(new Attempt(host, port));
    }

    public static final String RESULT_BUNDLE_NAME = "ExplicitConnectionBundle";
    public static final String RESULT_BUNDLE_SOCKET = "socket";
    public static final String RESULT_BUNDLE_GROUP_INFO = "groupInfo";
    //public static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.EXPLICIT_CONNECTION_RESULT";
}
