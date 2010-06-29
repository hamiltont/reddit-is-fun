package com.andrewshu.android.reddit.shareImage;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;

/**
 * Version 1 of uploading an image will just show a spinning image until the
 * upload returns or fails. Version 2 can show an image upload bar. Apparently
 * HttpClient supports progress indications on uploads
 * 
 *@see <a href="http://code.google.com/p/imgur-api/wiki/ImageUploading">imgur
 *      upload API</a>
 * 
 * @author Hamilton Turner <hamiltont@gmail.com>
 */
public class UploadAsyncTask extends AsyncTask<Uri, String, String> {

	private static final String IMGUR_API_KEY = "347ec991d0079db6ea067c8471b74348";
	private static final String IMGUR_POST_URI = "http://imgur.com/api/upload.json";

	private Context context_;
	private Test test_;

	public UploadAsyncTask(Test t) {
		super();
		context_ = t.getApplicationContext();

		test_ = t;
	}

	@Override
	protected void onPostExecute(String result) {
		super.onPostExecute(result);

		test_.onPostUpload(result);
	}

	@Override
	protected void onProgressUpdate(String... progress) {
		test_.onUploadStateProgress(progress[0]);
	}

	/**
	 * The passed {@link Uri} should match {@link Intent#EXTRA_STREAM}
	 */
	@Override
	protected String doInBackground(Uri... params) {
		if (params.length != 1)
			throw new IllegalArgumentException(
					"Can only upload a single image to imgur at a time");

		final Uri uri = params[0];

		JSONObject jsonResponse = sendToImgur(uri);
		if (jsonResponse == null)
			return null;

		// Now dig out the image data
		final String imgurLink = getImgurLink(jsonResponse);

		return imgurLink;
	}

	/**
	 * Attempts to load the image from the given Uri and upload it to imgur.
	 * Informs the user when (and why, if possible) an error occurs in
	 * contacting the Imgur site. Note that this method simply contacts the
	 * site, attempts the upload, and reports the API's response. Any errors
	 * returned by the API should be handled elsewhere.
	 * 
	 * @param uri
	 *            The URI that points to the InputStream for the image
	 * @return The JSON response from Imgur, or null.
	 */
	private JSONObject sendToImgur(Uri uri) {

		// Open the InputStream
		InputStream is = null;
		publishProgress("Opening image stream . . . ");
		try {
			is = context_.getContentResolver().openInputStream(uri);
		} catch (FileNotFoundException e) {
			Toast.makeText(context_, "Unable to load image", Toast.LENGTH_LONG)
					.show();
			e.printStackTrace();
			return null;
		}

		// Create the post
		publishProgress("Creating the post . . . ");
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(IMGUR_POST_URI);
		MultipartEntity entity = new MultipartEntity(
				HttpMultipartMode.BROWSER_COMPATIBLE);

		publishProgress("Adding image to post . . . ");
		HttpResponse response;
		String jsonResponse = null;
		try {
			// Note that the imgur api seems to need some filename to recognize
			// the image part as binary file. Passing a string versus null for
			// the filename changes the headers for the image part of the
			// multipart post slightly (adds "filename=something" to
			// Content-Disposition)

			// Inserting null seems to make imgur interpret everything sent
			// to it as a URL that it should attempt to query for an image file.
			// Returns error "1003 Invalid image type or URL" and recommends you
			// use the JPEG format in the returned error message.
			final InputStreamBody isb = new InputStreamBody(is,
					"uploaded-from-reddit-is-fun");
			entity.addPart("image", isb);
			entity.addPart("key", new StringBody(IMGUR_API_KEY));
			post.setEntity(entity);

			publishProgress("Uploading . . . ");
			response = client.execute(post);

			// TODO Might be nice to show a better error message
			final int code = response.getStatusLine().getStatusCode();
			if (code != 200) {
				// TODO - Fix this. Cannot access context_ in this manner in a
				// different thread
				// Toast.makeText(context_, "Unable to contact Imgur",
				// Toast.LENGTH_LONG).show();
				return null;
			}

			// Read in the response
			publishProgress("Downloading response . . . ");
			InputStream content = response.getEntity().getContent();
			ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
			final int BUF_SIZE = 1 << 8; // 1KiB buffer
			byte[] buffer = new byte[BUF_SIZE];
			int bytesRead = -1;
			while ((bytesRead = content.read(buffer)) > -1) {
				responseBody.write(buffer, 0, bytesRead);
			}
			content.close();

			jsonResponse = responseBody.toString();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			Toast.makeText(context_, "Could not save image", Toast.LENGTH_LONG)
					.show();
		} catch (IOException e) {
			Toast.makeText(context_, "Could not save image", Toast.LENGTH_LONG)
					.show();
			e.printStackTrace();
		}

		// Convert to JSON object
		JSONObject jo = null;
		try {
			jo = new JSONObject(jsonResponse);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return jo;
	}

	private String getImgurLink(JSONObject jsonResponse) {
		publishProgress("Parsing response . . . ");

		try {
			JSONObject rsp = jsonResponse.getJSONObject("rsp");
			String stat = rsp.getString("stat");
			if (stat.equalsIgnoreCase("ok"))
				return rsp.getJSONObject("image").getString("imgur_page");

			StringBuffer error = new StringBuffer("Imgur Error: ");
			String code = rsp.getString("error_code");
			String msg = rsp.getString("error_msg");
			error.append(code);
			error.append(" - ");
			error.append(msg);
			Toast.makeText(context_, error.toString(), Toast.LENGTH_LONG)
					.show();
		} catch (JSONException je) {
			Toast.makeText(context_, "Unable to get Imgur link",
					Toast.LENGTH_LONG).show();
		}

		return null;
	}

}