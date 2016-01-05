package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.massimodz8.collaborativegrouporder.networkMessage.ServerInfoRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
        // Android 17 cannot but Object in bundles so we cannot really save the socket.
        //public Socket socket;
        //public ConnectedGroup cg;
        Attempt successful; // OFC it might not be able to suceed again... :(
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
                Shaken hello = new Shaken();
                try {
                    JoinGroupActivity.ReadyGroup group = JoinGroupActivity.initialConnect(params[0].addr, params[0].port);
                    group.sock.close();
                } catch (UnknownHostException e) {
                    hello.ohno = new Shaken.Error(null, getString(R.string.badHost_msg));
                    hello.ohno.refocus = R.id.in_explicit_inetAddr;
                } catch (IOException e) {
                    hello.ohno = new Shaken.Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
                } catch (ClassNotFoundException e) {
                    hello.ohno = new Shaken.Error(null, getString(R.string.badInitialServerReply_msg));
                } catch (ClassCastException e) {
                    hello.ohno = new Shaken.Error(null, getString(R.string.unexpectedInitialServerReply_msg));
                }
                //hello.socket = pipe;
                hello.successful = params[0];
                return hello;
            }

            @Override
            protected void onPostExecute(Shaken shaken) {
                if(shaken.ohno != null) {
                    AlertDialog.Builder build = new AlertDialog.Builder(self);
                    if(shaken.ohno.title != null && !shaken.ohno.title.isEmpty()) build.setTitle(shaken.ohno.title);
                    if(shaken.ohno.msg != null && !shaken.ohno.msg.isEmpty()) build.setMessage(shaken.ohno.msg);
                    if(shaken.ohno.refocus != null) findViewById(shaken.ohno.refocus).requestFocus();
                    build.show();
                    return;
                }
                Intent result = new Intent(RESULT_ACTION);
                result.putExtra(RESULT_EXTRA_INET_ADDR, shaken.successful.addr);
                result.putExtra(RESULT_EXTRA_PORT, shaken.successful.port);
                setResult(Activity.RESULT_OK, result);
                finish();
            }
        }.execute(new Attempt(host, port));
    }



    public static final String RESULT_EXTRA_INET_ADDR = "address";
    public static final String RESULT_EXTRA_PORT = "port";
    public static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.EXPLICIT_CONNECTION_RESULT";
}
