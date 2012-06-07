package com.vgmoose.jwiiload;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.zip.*;

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WiiloadActivity extends Activity implements OnClickListener {

	public static Context getAppContext() {
		return context;
	}

	private static Context context;
	static Socket socket;
	static String host;
	static int port = 4299;

	static Button open;
	static Button scan;
	static Button send;

	static String ip2;

	static String s;
	static String arguments = "";
	static File filename;

	static File compressed;
	static  boolean stopscan = false;
	static boolean fext=true;

	static TextView status;
	static TextView fname;
	static String lastip = "0.0.0.0";
	static EditText wiiip;

	public static void tripleScan()
	{
		stopscan = false;
		for (int x=1; x<3; x++)
		{
			scan(x);
			if (host!=null)
				break;
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();

		status = new TextView(context);
		fname = new TextView(context);
		setContentView(R.layout.main);

		wiiip = (EditText)findViewById(R.id.editText1);
//		wiiip.setText("Hello");
		
		updateStatus("Ready to send data");
		open = (Button)(findViewById(R.id.button3));
		scan = (Button)(findViewById(R.id.button2));
		send = (Button)(findViewById(R.id.button1));
//		b.setText("Choose File");
//		c.setText("Send to Wii");
		send.setEnabled(false);
//		a.addView(b);
//		a.addView(status);

		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		int ipAddress = wifiInfo.getIpAddress();
		ip2 = intToIp(ipAddress);

//		a.addView(c,params);  
//		a.addView(fname, params2);
		open.setOnClickListener(this);
		scan.setOnClickListener(this);
		send.setOnClickListener(this);

	}

	public void onClick(View v) {
		if (v==open)
		{
			Intent intent = new Intent(this, FileChooser.class);
			this.startActivity(intent);
		}
		else if (v==send)
		{
			new Thread()
			{
				@Override
				public void run()
				{
					wiisend();
				}
			}.start();
		}
		else if (v==scan)
		{
			scan.setEnabled(false);
			wiiip.setEnabled(false);
			new Thread()
			{
				@Override
				public void run()
				{
					tripleScan();
					handler.sendEmptyMessage(0);
				}
			}.start();
		}
	}
	
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        		scan.setEnabled(true);
        		wiiip.setEnabled(true);
        		if (host!=null && !host.equals("rate"))
        			wiiip.setText(host);

        }
};

	public static void compressData()
	{
		try
		{
			// Compress the file to send it faster
			updateStatus("Compressing data...");
			compressed = compressFile(filename);
			updateStatus("Data compressed!");

		} catch(Exception e){
			// Fall back in case compressed file can't be written
			compressed = filename;
		}
	}



	public static void wiisend()
	{

		try
		{
			// Open socket to wii with host and port and setup output stream
			if (host==null)
				socket = new Socket(host, port);

			compressData();

			updateStatus("Talking to Wii...");

			OutputStream os = socket.getOutputStream();
			DataOutputStream dos = new DataOutputStream(os);

			updateStatus("Preparing data...");

			byte max = 0;
			byte min = 5;

			short argslength = (short) (filename.getName().length()+arguments.length()+1);

			int clength = (int) (compressed.length());  // compressed filesize
			int ulength = (int) (filename.length());        // uncompressed filesize

			// Setup input stream for sending bytes later of compressed file
			InputStream is = new FileInputStream(compressed);
			BufferedInputStream bis = new BufferedInputStream(is);

			byte b[]=new byte[128*1024];
			int numRead=0;

			updateStatus("Talking to Wii...");

			dos.writeBytes("HAXX");

			dos.writeByte(max);
			dos.writeByte(min);

			dos.writeShort(argslength);

			dos.writeInt(clength);  // writeLong() sends 8 bytes, writeInt() sends 4
			dos.writeInt(ulength);

			//dos.size();   // Number of bytes sent so far, should be 16

			updateStatus("Sending "+filename.getName());
			Log.d("NETWORK","Sending "+filename.getName()+"...");
			dos.flush();

			while ( ( numRead=bis.read(b)) > 0) {
				dos.write(b,0,numRead);
				dos.flush();
			}
			dos.flush();

			updateStatus("Talking to Wii...");
			if (arguments.length()!=0)
				Log.d("NETWORK","Sending arguments...");
			else
				Log.d("NETWORK","Finishing up...");

			dos.writeBytes(filename.getName()+"\0");

			String[] argue = arguments.split(" ");

			for (String x : argue)
				dos.writeBytes(x+"\0");

			updateStatus("All done!");
			Log.d("NETWORK","\nFile transfer successful!");

			lastip = host;

			if (compressed!=filename)
				compressed.delete();

		}
		catch (Exception ce)
		{
			updateStatus("No Wii found");
			//                    int a=0;

			if (host==null)
				host="";

			Log.d("NETWORK","No Wii found at "+host+"!");

			//                    if (!cli)
			//                    {
			//                            if (host.equals("rate"))
			//                                    a = framey.showRate();
			//                            else
			//                                    a= framey.showLost();
			//                    }
			//                  
			//                    if (a==0)
			//                    {
			//                            tripleScan();
			//                            wiisend();
			//                    }

		}
	}

	static void updateName()
	{
		fname.setText(filename.getName());
		send.setEnabled(true);
	}

	static void updateStatus(String s)
	{
		Log.d("STRING",s);
	}

	public String intToIp(int i) {

		// get xxx.xxx.xxx. to prepare for search
		return (i & 0xFF ) + "." +
		((i >> 8 ) & 0xFF) + "." +
		((i >> 16 ) & 0xFF) + ".";
	}

	static void scan(int t)
	{                       
		host=null;

		updateStatus("Finding Wii...");
		Log.d("NETWORK","Searching for a Wii...");
		String output = null;

		// this code assumes IPv4 is used
		String ip = ip2;	

		Log.d("ip2",ip2);

		for (int i = 1; i <= 254; i++)
		{
			try
			{
				ip = ip2 + i; 
				InetAddress address = InetAddress.getByName(ip);
				//				Log.d("NETWORK","Checking "+ip);

				if (address.isReachable(10*t))
				{
					output = address.toString().substring(1);
					Log.d("NETWORK",output + " is on the network");

					// Attempt to open a socket
					try
					{
						socket = new Socket(output,port);
						Log.d("NETWORK","and is potentially a Wii!");
						updateStatus("Wii found!");
//						 wiiip.setText(output);

						host=output;
						return;
					} catch (Exception e) {
						e.printStackTrace();
					}

				}
			} catch (ConnectException e) {
				updateStatus("Rate limited");
				host="rate";
				e.printStackTrace();
				return;
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}

			if (stopscan)
			{
				updateStatus("Scan aborted");
				Log.d("NETWORK","Scan aborted");
				break;
			}
		} 

		return;

	}
	
	  public boolean onCreateOptionsMenu(Menu menu) {
	        menu.add(0, 1, 2, "Arguments");
	        menu.add(0,1,2,"Change Port");
	        menu.add(0,1,2,"Display Ad");
	        return true;
	    }
	 
	    /* Handles item selections */
	    public boolean onOptionsItemSelected(MenuItem item) {
	        switch (item.getItemId()) {
	    	case 1:
	            return true;
	        }
	        return false;
	    }

	public static File compressFile(File raw) throws IOException
	{
		File compressed = new File(filename+".wiiload.gz");
		InputStream in = new FileInputStream(raw);
		OutputStream out =
			new DeflaterOutputStream(new FileOutputStream(compressed));
		byte[] buffer = new byte[1000];
		int len;
		while((len = in.read(buffer)) > 0) {
			out.write(buffer, 0, len);
		}
		in.close();
		out.close();
		return compressed;
	}

	public class BannerExample extends Activity {
		  private AdView adView;

		  @Override
		  public void onCreate(Bundle savedInstanceState) {
		    super.onCreate(savedInstanceState);
		    setContentView(R.layout.main);

		    // Create the adView
		    adView = new AdView(this, AdSize.BANNER, "a14fce128a3b08a");

		    // Lookup your LinearLayout assuming it�s been given
		    // the attribute android:id="@+id/mainLayout"
		    LinearLayout layout = (LinearLayout)findViewById(R.id.mainLayout);

		    // Add the adView to it
		    layout.addView(adView);

		    // Initiate a generic request to load it with an ad
		    adView.loadAd(new AdRequest());
		  }

		  @Override
		  public void onDestroy() {
		    if (adView != null) {
		      adView.destroy();
		    }
		    super.onDestroy();
		  }
		}
}