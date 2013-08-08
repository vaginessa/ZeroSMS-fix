package com.android.zerosms;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.Toast;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SMSDispatcher;

public class ZeroSMS extends Activity
{
  private Button btnSendSMS;
  private RadioButton radioVm1, radioVm0, radioFlash;
  private Switch m_receiptSwitch;
  private ImageButton btnContactPick;
  private EditText txtPhoneNo;
  private EditText txtMessage;
  private static final int CONTACT_PICKER_RESULT = 1001;
  private String TAG = "ZeroSMS";

  /**
   * Called when the activity is first created.
   */
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    btnSendSMS = (Button)findViewById(R.id.btnSendSMS);
    btnContactPick = (ImageButton)findViewById(R.id.btnContact);
    txtPhoneNo = (EditText)findViewById(R.id.txtPhoneNo);
    txtMessage = (EditText)findViewById(R.id.txtMessage);
    radioVm1 = (RadioButton)findViewById(R.id.radioButtonVM1);
    radioVm0 = (RadioButton)findViewById(R.id.radioButtonVM0);
    radioFlash = (RadioButton)findViewById(R.id.radioButtonFlash);
    m_receiptSwitch = (Switch)findViewById(R.id.receipt);

    btnSendSMS.setOnClickListener(new View.OnClickListener()
    {
      public void onClick(View v)
      {
        String phoneNo = txtPhoneNo.getText().toString();
        String message = txtMessage.getText().toString();
        if (phoneNo.length() > 0 && message.length() > 0)
        {
          if (!sendSMS(phoneNo, message))
          {
            Toast.makeText(getBaseContext(), "An error occured while sending SMS.", Toast.LENGTH_SHORT).show();
          }
          else
          {
            Toast.makeText(getBaseContext(), "SMS sent =)", Toast.LENGTH_SHORT).show();
          }
        }
        else
        {
          Toast.makeText(getBaseContext(), "Please enter both phone number and message.", Toast.LENGTH_SHORT).show();
        }
      }
    });

    btnContactPick.setOnClickListener(new View.OnClickListener()
    {
      public void onClick(View v)
      {
        Intent contactPickerIntent = new Intent(Intent.ACTION_PICK);
        contactPickerIntent.setData(Contacts.CONTENT_URI);
        contactPickerIntent.setType(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(contactPickerIntent, CONTACT_PICKER_RESULT);
      }
    });
  }

  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    Log.d("ZeroSMS", "Result: " + String.valueOf(resultCode));
    if (resultCode == -1)
    {
      switch (requestCode)
      {
        case CONTACT_PICKER_RESULT:
          Cursor cursor = null;
          try
          {
            Uri result = data.getData();
            Log.d("ZeroSMS", result.toString());
            cursor = getContentResolver().query(result, null, android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, null, null);
            if (cursor.moveToFirst())
            {
              txtPhoneNo.setText(cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)));
            }
          }
          catch (Exception e)
          {
            Log.e("ZeroSMS", "An error occured while picking contact.");
          }
          finally
          {
            if (cursor != null)
            {
              cursor.close();
            }
          }
          break;
      }
    }
    else
    {
      // gracefully handle failure
      Log.w("ZeroSMS", "Warning: activity result not ok");
    }
  }

  /* Sends class 0 SMS */
  private boolean sendSMS(String phoneNumber, String message)
  {
    int size;
    Field f;

    Log.d(TAG, "Retrieving phone instance ...");
    Phone phone = PhoneFactory.getDefaultPhone();

	    	/* Get IccSmsInterfaceManager */
    Log.d(TAG, "Retrieving SmsInterfaceManager ...");
    IccSmsInterfaceManager ismsm = phone.getIccSmsInterfaceManager();

    try
    {
      Log.d(TAG, "Retrieving mDispatcher ...");
      f = IccSmsInterfaceManager.class.getDeclaredField("mDispatcher");
      f.setAccessible(true);
      SMSDispatcher sms_disp = (SMSDispatcher)f.get(ismsm);

      Log.d(TAG, "Formatting class 0 SMS ...");
      byte[] b = new byte[0];
      SmsMessage.SubmitPdu pdus =
          SmsMessage.getSubmitPdu(
              null, phoneNumber, message, false
          );

		  /* change class to Class 0 */
      size = (int)pdus.encodedMessage[2];
      Log.d(TAG, "Size: " + size);
      StringBuilder sb = new StringBuilder();
      for (byte bajt : pdus.encodedMessage)
      {
        sb.append(String.format("%02X", bajt));
      }
      size = (size / 2) + (size % 2);
      Log.d(TAG, "Location of class: " + size + 5);
      Log.d(TAG, "Encoded message - before: " + sb.toString());

      if (radioVm1.isChecked())
      {
        pdus.encodedMessage[size + 5] = (byte)0xC8; //voicemail on
      }
      else if (radioVm0.isChecked())
      {
        pdus.encodedMessage[size + 5] = (byte)0xC0; //voicemail off
      }
      else if (radioFlash.isChecked())
      {
        pdus.encodedMessage[size + 5] = (byte)0xF0; //flash
      }

      if (m_receiptSwitch.isChecked())
      {
        pdus.encodedMessage[0] = (byte)0xA1; //receipt
      }

      sb = new StringBuilder();
      for (byte bajt : pdus.encodedMessage)
      {
        sb.append(String.format("%02X", bajt));
      }
      Log.d(TAG, "Encoded message after fix: " + sb.toString());

		  /* send raw pdu */
      Log.d(TAG, "Sending SMS via sendRawPdu() ...");
      try
      {
		   		/* Android 2.2 -> 4.0.* */
        Method m = SMSDispatcher.class.getDeclaredMethod("sendRawPdu", b.getClass(), b.getClass(), PendingIntent.class, PendingIntent.class);
        m.setAccessible(true);
        m.invoke(sms_disp, pdus.encodedScAddress, pdus.encodedMessage, null, null);
      }
      catch (NoSuchMethodException e)
      {
		   		/* Android 4.1.2 */
        Method m = SMSDispatcher.class.getDeclaredMethod("sendRawPdu", b.getClass(), b.getClass(), PendingIntent.class, PendingIntent.class, String.class);
        m.setAccessible(true);
        m.invoke(sms_disp, pdus.encodedScAddress, pdus.encodedMessage, null, null, phoneNumber);
      }
      Log.d(TAG, "SMS sent");
      return true;
    }
    catch (SecurityException e)
    {
      Log.e(TAG, "Exception: Security !");
      e.printStackTrace();
      return false;
    }
    catch (NoSuchFieldException e)
    {
      Log.e(TAG, "Exception: Field mDispatcher not found !");
      e.printStackTrace();
      return false;
    }
    catch (IllegalArgumentException e)
    {
      Log.e(TAG, "Exception: Illegal Argument !");
      e.printStackTrace();
      return false;
    }
    catch (IllegalAccessException e)
    {
      Log.e(TAG, "Exception: Illegal access !");
      e.printStackTrace();
      return false;
    }
    catch (NoSuchMethodException e)
    {
      Log.e(TAG, "Exception: sendRawPdu() not found !");
      e.printStackTrace();
      return false;
    }
    catch (InvocationTargetException e)
    {
      Log.e(TAG, "Exception: cannot invoke sendRawPdu() !");
      e.printStackTrace();
      return false;
    }
  }
}