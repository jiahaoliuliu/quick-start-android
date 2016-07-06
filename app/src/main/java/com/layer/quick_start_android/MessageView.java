package com.layer.quick_start_android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Takes a Layer Message object, formats the text and attaches it to a LinearLayout
 */
public class MessageView {

    //The parent object (in this case, a LinearLayout object with a ScrollView parent)
    private LinearLayout myParent;
    //The sender and message views
    private TextView senderTV;
    private TextView messageTV;
    private ImageView pictureIV;
    private ImageView statusImage;
    private static Bitmap EMPTY_BITMAP = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_4444);
    private Bitmap image;
    private LinearLayout messageDetails;

    //Takes the Layout parent object and message
    public MessageView(LinearLayout parent, Message msg) {
        myParent = parent;
        List<MessagePart> parts = msg.getMessageParts();

        //The first part of each message will include the sender and status
        messageDetails = new LinearLayout(parent.getContext());
        messageDetails.setOrientation(LinearLayout.HORIZONTAL);
        myParent.addView(messageDetails);

        //Creates the sender text view, sets the text to be italic, and attaches it to the parent
        senderTV = new TextView(parent.getContext());
        senderTV.setTypeface(null, Typeface.ITALIC);
        messageDetails.addView(senderTV);

        //Creates the message text view and attaches it to the parent
        messageTV = new TextView(parent.getContext());
        myParent.addView(messageTV);

        if (parts.get(0).getMimeType().startsWith("image")) {
            pictureIV = new ImageView(parent.getContext());
            myParent.addView(pictureIV);
        }

        //The status is displayed with an icon, depending on whether the message has been read, delivered, or sent
        if (msg.getSender().getUserId() != null) {
            statusImage = new ImageView(parent.getContext());
            statusImage = createStatusImage(msg);//statusImage.setImageResource(R.drawable.sent);
            messageDetails.addView(statusImage);
        }


        //Populates the text views
        UpdateMessage(msg);
    }


    //Takes a message and sets the text in the two text views
    public void UpdateMessage(Message msg) {

        List<MessagePart> parts = msg.getMessageParts();
        String senderTxt = craftSenderText(msg);
        String msgTxt = craftMsg(msg);

        senderTV.setText(senderTxt);
        if (parts.get(0).getMimeType().startsWith("image")) {
            pictureIV.setImageBitmap(image);
        } else {
            messageTV.setText(msgTxt);
        }
    }

    //The sender text is formatted like so:
    //  User @ Timestamp - Status
    private String craftSenderText(Message msg) {

        //The User ID
        String senderTxt;
        if (msg.getSender().getName() != null) {
            senderTxt = msg.getSender().getName();
        } else {
            senderTxt = msg.getSender().getUserId();
        }

        //Add the timestamp
        if (msg.getSentAt() != null) {
            senderTxt += " @ " + new SimpleDateFormat("HH:mm:ss").format(msg.getReceivedAt());
        }

        //Add some formatting before the status icon
        senderTxt += "   ";

        //Return the formatted text
        return senderTxt;
    }

    //Checks the recipient status of the message (based on all participants)
    private Message.RecipientStatus getMessageStatus(Message msg) {

        //If we didn't send the message, we already know the status - we have read it
        if (!msg.getSender().getUserId().equalsIgnoreCase(MainActivity.getUserID()))
            return Message.RecipientStatus.READ;

        //Assume the message has been sent
        Message.RecipientStatus status = Message.RecipientStatus.SENT;

        //Go through each user to check the status, in this case we check each user and prioritize so
        // that we return the highest status: Sent -> Delivered -> Read
        for (int i = 0; i < MainActivity.getAllParticipants().size(); i++) {

            //Don't check the status of the current user
            String participant = MainActivity.getAllParticipants().get(i);
            if (participant.equalsIgnoreCase(MainActivity.getUserID()))
                continue;

            if (status == Message.RecipientStatus.SENT) {

                if (msg.getRecipientStatus(participant) == Message.RecipientStatus.DELIVERED)
                    status = Message.RecipientStatus.DELIVERED;

                if (msg.getRecipientStatus(participant) == Message.RecipientStatus.READ)
                    return Message.RecipientStatus.READ;

            } else if (status == Message.RecipientStatus.DELIVERED) {
                if (msg.getRecipientStatus(participant) == Message.RecipientStatus.READ)
                    return Message.RecipientStatus.READ;
            }
        }

        return status;
    }

    //Checks the message parts and parses the message contents
    private String craftMsg(Message msg) {

        //The message text
        String msgText = "";

        //Go through each part, and if it is text (which it should be by default), append it to the
        // message text
        List<MessagePart> parts = msg.getMessageParts();
        for (int i = 0; i < msg.getMessageParts().size(); i++) {

            //You can always set the mime type when creating a message part, by default the mime type
            // is initialized to plain text when the message part is created
            if (parts.get(i).getMimeType().equalsIgnoreCase("text/plain")) {
                try {
                    msgText += new String(parts.get(i).getData(), "UTF-8") + "\n";
                } catch (UnsupportedEncodingException e) {

                }
            } else if (parts.get(i).getMimeType().startsWith("image")) {
                image = getResizedImage(parts.get(i), 100);
            }
        }

        //Return the assembled text
        return msgText;
    }

    private Bitmap getResizedImage(MessagePart part, final int width) {
        Bitmap bitmap = null;

        try {
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(part.getDataStream(), null, bitmapOptions);

            final int currentWidth = bitmapOptions.outWidth;
            final int currentHeight = bitmapOptions.outHeight;

            // Scale with correct aspect ratio
            double desiredWidth = width;

            int sampleSize = 1;
            if (desiredWidth > 0 && desiredWidth < currentWidth) {
                sampleSize = (int) Math.ceil(currentWidth / desiredWidth);
            }

            int requiredMemory = (currentWidth / sampleSize) * (currentHeight / sampleSize) * 4;

            // if there is no enough memory still increase downSampling
            while (requiredMemory > getAvailableMemory()) {
                sampleSize++;
                requiredMemory = (currentWidth / sampleSize) * (currentHeight / sampleSize) * 4;
                if (sampleSize > 100) {
                    System.out.println("getResizedImage() sampled till 100 and still didn't work");
                    break;
                }
            }
            bitmapOptions.inJustDecodeBounds = false;
            bitmapOptions.inSampleSize = sampleSize;
            bitmap = BitmapFactory.decodeStream(part.getDataStream(), null, bitmapOptions);
            if (bitmap == null) {
                return EMPTY_BITMAP;
            }

            System.gc();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return bitmap == null ? EMPTY_BITMAP : bitmap;
    }

    public static long getAvailableMemory() {
        Runtime runtime = Runtime.getRuntime();
        final long availableMemory;
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        availableMemory = (runtime.maxMemory() - usedMemory);

        return availableMemory;
    }

    //Sets the status image based on whether other users in the conversation have received or read
    //the message
    private ImageView createStatusImage(Message msg) {
        ImageView status = new ImageView(myParent.getContext());

        switch (getMessageStatus(msg)) {

            case SENT:
                status.setImageResource(R.drawable.sent);
                break;

            case DELIVERED:
                status.setImageResource(R.drawable.delivered);
                break;

            case READ:
                status.setImageResource(R.drawable.read);
                break;
        }

        //Have the icon fill the space vertically
        status.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        return status;
    }
}