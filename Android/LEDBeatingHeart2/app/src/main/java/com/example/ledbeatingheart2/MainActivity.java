package com.example.ledbeatingheart2;

// cristinel ababei; 2016;
// this is a simple app with just two buttons, to control two different LED effects
// on an 8x8 LED matrix connected to an Arduino board; the control signal is sent
// to the Arduino board via WiFi as effect=1 or effect=2; the Arduino board has
// attached to it an ESP8266 breakout module (cheapest and super easy to use), which will
// be the "Server" in the connection between itself and the Android smartphone running this app;
// if button buttonHeartBeat is pressed, we'll send a "1" to the Arduino, meaning
// we want to toggle the first heart effect, which is HEART BEATING;
// if button buttonHeartFade is pressed, we'll send a "2" to the Arduino, meaning
// we want to toggle the second heart effect, which is HEART FADING;
// CREDITS:
// 1) this code has been put together from several different online sources, including
//    Android documentation;
// 2) I used some colors I found at: http://www.color-hex.com/color-palettes/

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity implements View.OnClickListener {
    public final static String PREF_IP = "PREF_IP_ADDRESS";
    public final static String PREF_PORT = "PREF_PORT_NUMBER";
    // declare buttons and text inputs
    private Button buttonHeartBeat, buttonHeartFade;
    private EditText editTextIPAddress, editTextPortNumber;
    // shared preferences objects used to save the IP address and port so that the user doesn't have to
    // type them next time he/she opens the app.
    SharedPreferences.Editor editor;
    SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences("HTTP_HELPER_PREFS",Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        // assign text inputs
        editTextIPAddress = (EditText)findViewById(R.id.editTextIPAddress);
        editTextPortNumber = (EditText)findViewById(R.id.editTextPortNumber);
       
        // assign buttons, which are private member variables of this class; 
        buttonHeartBeat = (Button)findViewById(R.id.buttonHeartBeat);
        buttonHeartBeat.setPadding(0, 0, 0, 0);
        buttonHeartBeat.setTextColor(Color.parseColor("#7f3c3c"));
        buttonHeartBeat.setBackgroundResource(R.drawable.mybutton); // mybutton.xml that controls normal, pressed, focused, etc; 
        buttonHeartFade = (Button)findViewById(R.id.buttonHeartFade);
        buttonHeartFade.setPadding(0, 0, 0, 0);
        buttonHeartFade.setTextColor(Color.parseColor("#7f3c3c"));
        buttonHeartFade.setBackgroundResource(R.drawable.mybutton); // mybutton.xml that controls normal, pressed, focused, etc;

        // set button listener (this class);
        buttonHeartBeat.setOnClickListener(this);
        buttonHeartFade.setOnClickListener(this);        
 
        // get the IP address and port number from the last time the user used the app,
        // put an empty string "" is this is the first time.
        editTextIPAddress.setText(sharedPreferences.getString(PREF_IP,""));
        editTextPortNumber.setText(sharedPreferences.getString(PREF_PORT,""));
    }
 
 
    @Override
    public void onClick(View view) {
        // get the pin number
        String parameterValue = "";
        // get the ip address
        String ipAddress = editTextIPAddress.getText().toString().trim();
        // get the port number
        String portNumber = editTextPortNumber.getText().toString().trim();
 
        // save the IP address and port for the next time the app is used
        editor.putString(PREF_IP,ipAddress); // set the ip address value to save
        editor.putString(PREF_PORT,portNumber); // set the port number to save
        editor.commit(); // save the IP and PORT
 
        // get the number from the button that was clicked;
        // if button buttonHeartBeat is pressed, we'll send a "1" to the Arduino, meaning
        // we want to toggle the first heart effect, which is Beat;
        // if button buttonHeartFade is pressed, we'll send a "2" to the Arduino, meaning
        // we want to toggle the second heart effect, which is Fade;
        if (view.getId()==buttonHeartBeat.getId()) {
            parameterValue = "1";
        } else if (view.getId()==buttonHeartFade.getId()) {
            parameterValue = "2"; 
        }
 
        // execute HTTP request via an instance of a new local class (defined later below);
        // in current implementation of our application, we only use one parameter called "effect";
        // which has value 1 if first button is pressed or 2 if second button is pressed;
        // that information effect=1 or effect=2 will be sent as an HTTP POST;
        if (ipAddress.length()>0 && portNumber.length()>0) {
            new HttpRequestAsyncTask(
            		view.getContext(), parameterValue, ipAddress, portNumber, "effect" 
            	).execute();
        }
    }


    // this is the function that actually sends the HTTP POST; note that it is
    // actually called via a wrapper class HttpRequestAsyncTask;
	// Description: Send an HTTP Get request to a specified ip address and port.
	// Also send a parameter "parameterName" with the value of "parameterValue".
	// @param parameterValue the pin number to toggle
	// @param ipAddress the ip address to send the request to
	// @param portNumber the port number of the ip address
	// @param parameterName
	// @return The ip address' reply text, or an ERROR message is it fails to receive one    
    public String sendRequest(String parameterValue, String ipAddress, String portNumber,
                              String parameterName) throws Exception {
        // response from the ESP8266 server;
        String serverResponse = "";

        // create list with the parameters to send; for now, we only work with one parameter though;
        String requestURL = "http://" + ipAddress + ":" + portNumber + "/?";
        List<NameValuePair> params_to_send = new ArrayList<NameValuePair>();
        params_to_send.add(new NameValuePair(parameterName, parameterValue)); // add parameter "effect" with value "1" or "2";
        // if you wanted to add more parameter-value pairs:
        //params_to_send.add(new BasicNameValuePair("Param2", paramValue2));
        //params_to_send.add(new BasicNameValuePair("Param3", paramValue3));
        // NOTE: for situations like this, with only one parameter, we could have just done:
        // String requestURL = "http://"+ipAddress+":"+portNumber+"/?"+parameterName+"="+parameterValue;
        // e.g., http://myIpaddress:myport/?pin=13 (to toggle pin 13 for example)
        // without the need for constructing the "params_to_send" list and BufferedWriter later;
        // but, this is a more generic approach that will allow us later to include
        // possibly more parameters to send if needed;

        try {
        	// create the URL object, and set the connection so that we can write to it;
            URL url = new URL(requestURL);            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(3000);
            conn.setConnectTimeout(3000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            // create an output stream on created connection and open an OutputStreamWriter on it;
            // if the URL does not support output, getOutputStream method throws an UnknownServiceException;
            // if the URL does support output, then this method returns an output stream that is connected 
            // to the input stream of the URL on the server side the client's output is the server's input;
            // write the required information to the output stream and close the stream;
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter( new OutputStreamWriter(os, "UTF-8") );
            writer.write( generate_PostDataString(params_to_send) ); // write effect=1 or effect=2
            writer.flush();
            writer.close();
            os.close();

            // now we need to read the string the server has sent back, if any;
            // NOTE: currently, I have the ARduino sketch not send back a confirmation
            // (I commented that out);
            int responseCode=conn.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                String line;
                BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line=br.readLine()) != null) {
                	serverResponse+=line;
                }
            } else {
            	serverResponse="ERROR - No response from ESP8266";
            }
        } catch (Exception e) {
        	serverResponse = e.getMessage();
            e.printStackTrace();
        }
        
        // return the server's response text;
        return serverResponse;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // utility functions
    //
    ///////////////////////////////////////////////////////////////////////////////

    // I create my own local NameValuePair class as a substitute for the
    // one that used to come with org.apache.http.NameValuePair;
    // used here it to create lists for http connections;
    public static class NameValuePair {
        private final String name;
        private final String value;
        public NameValuePair(final String name, final String value) {
            this.name = name;
            this.value = value;
        }
        String getName() {
            return name;
        }
        String getValue() {
            return value;
        }
    }

    // this function takes a list of parameters (names and values) and creates a string
    // out of them to be sent out by the HTTP POST; this string will be appended right after
    // the hard-coded string "http://"+ipAddress+":"+portNumber+"/?"
    // this function is used inside sendRequest();
    private String generate_PostDataString(List<NameValuePair> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (NameValuePair pair : params) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }
        return result.toString();
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // class HttpRequestAsyncTask
    //
    ///////////////////////////////////////////////////////////////////////////////

    // define a new class to work with an AsyncTask; needed as a trick to execute HTTP requests
    // in the background so that they do not block the user interface;
    private class HttpRequestAsyncTask extends AsyncTask<Void, Void, Void> {
        // declare variables needed
        private String requestReply, ipAddress, portNumber;
        private Context context;
        private AlertDialog alertDialog;
        private String parameter;
        private String parameterValue;
 
        // Description: The asyncTask class constructor. Assigns the values used in its other methods.
        // @param context the application context, needed to create the dialog
        // @param parameterValue the pin number to toggle
        // @param ipAddress the ip address to send the request to
        // @param portNumber the port number of the ip address
        public HttpRequestAsyncTask(Context context, String parameterValue, 
        		String ipAddress, String portNumber, String parameter) {
            this.context = context;

            alertDialog = new AlertDialog.Builder(this.context)
                    .setCancelable(true)
                    .create();
            // next is a trick to create alertDialog as a Self-Dismissing dialog;
            // configure alertDialog to disappear after 2 seconds;
            alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button noButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    new CountDownTimer(2000, 1000) {
                        @Override
                        public void onTick(long l) {
                            noButton.setText("Close (" + ((l/1000) + 1) + ")");
                        }
                        @Override
                        public void onFinish() {
                            if (alertDialog.isShowing()) {
                                alertDialog.dismiss();
                            }
                        }
                    }.start();
                }
            });

            this.ipAddress = ipAddress;
            this.parameterValue = parameterValue;
            this.portNumber = portNumber;
            this.parameter = parameter;
        }
 
        // Name: doInBackground
        // Description: Sends the request to the ip address
        // @param voids
        // @return
        @Override
        protected Void doInBackground(Void... voids) {
            alertDialog.setTitle("SEND HTTP POST");
            alertDialog.setMessage("Sending to ESP8266, wait for reply...");
            if (!alertDialog.isShowing()) {
                alertDialog.show();
            }
            try {
                // call function sendRequest() of the host class of this class (this is weird
                // coding - should fix it); this is where actually HTTP POST will be done;
                // during its execution, it will store in "requestReply" the response from
                // the ESP8266 server; response if any will be printed by onPostExecute();
            	requestReply = sendRequest(parameterValue, ipAddress,portNumber, parameter);
            } catch (Exception e) {
            	e.printStackTrace();
            }	
            return null;
        }

        // Name: onPreExecute
        // Description: This function is executed before the HTTP request is sent to ip address.
        // The function will set the dialog's message and display the dialog.
        @Override
        protected void onPreExecute() {
            // NOTE: this will happen very fast; no time to actually see it;
            alertDialog.setTitle("SEND HTTP POST");
            alertDialog.setMessage("Getting ready to send...");
            if (!alertDialog.isShowing()) {
                alertDialog.show();
            }
        }

        // Name: onPostExecute
        // Description: This function is executed after the HTTP request returns from the ip address.
        // The function sets the dialog's message with the reply text from the server and displays
        // the dialog if it's not displayed already (in case it was closed by accident);
        // @param aVoid void parameter
        @Override
        protected void onPostExecute(Void aVoid) {
            // NOTE: I commented this out because Arduino is set not
            // to send back any confirmation response; if you change that in the
            // Arduino sletch, then, you can uncomment this here so that
            // we show the received response...
            //alertDialog.setTitle("RECEIVED RESPONSE");
            //alertDialog.setMessage(requestReply);
            //if (!alertDialog.isShowing()) {
            //    alertDialog.show();
            //}
        }
    } // class HttpRequestAsyncTask

} // class MainActivity
