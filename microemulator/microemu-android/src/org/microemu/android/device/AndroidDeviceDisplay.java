/**
 *  MicroEmulator
 *  Copyright (C) 2008 Bartek Teodorczyk <barteo@barteo.net>
 *
 *  It is licensed under the following two licenses as alternatives:
 *    1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 *    2. Apache License (the "AL") Version 2.0
 *
 *  You may not use this file except in compliance with at least one of
 *  the above two licenses.
 *
 *  You may obtain a copy of the LGPL at
 *      http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 *
 *  You may obtain a copy of the AL at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the LGPL or the AL for the specific language governing permissions and
 *  limitations.
 *
 *  @version $Id$
 */

package org.microemu.android.device;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.lcdui.DisplayUtils;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;

import org.microemu.MIDletBridge;
import org.microemu.android.MicroEmulatorActivity;
import org.microemu.android.device.ui.AndroidCanvasUI;
import org.microemu.android.device.ui.AndroidCanvasUI.CanvasView;
import org.microemu.app.ui.DisplayRepaintListener;
import org.microemu.device.DeviceDisplay;
import org.microemu.device.EmulatorContext;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.PowerManager;

public class AndroidDeviceDisplay implements DeviceDisplay {
	
	private Activity activity;
    
	private EmulatorContext context;
	
	private int displayRectangleWidth;
	
	private int displayRectangleHeight;
	
    private ArrayList<DisplayRepaintListener> displayRepaintListeners = new ArrayList<DisplayRepaintListener>();
    	
	private Rect rectangle = new Rect();
	
	public AndroidDeviceDisplay(Activity activity, EmulatorContext context, int width, int height) {
		this.activity = activity;
		this.context = context;
		setSize(width, height);
	}
	
	public void setSize(int width, int height) {
		if (MicroEmulatorActivity.config.ORIG_DISPLAY_FIXED) {
	        displayRectangleWidth = MicroEmulatorActivity.config.ORIG_DISPLAY_WIDTH;
	        displayRectangleHeight = MicroEmulatorActivity.config.ORIG_DISPLAY_HEIGHT;
		} else {
	        displayRectangleWidth = (int) (width * (MicroEmulatorActivity.config.CANVAS_AREA_RIGHT - MicroEmulatorActivity.config.CANVAS_AREA_LEFT));
	        displayRectangleHeight = (int) (height * (MicroEmulatorActivity.config.CANVAS_AREA_BOTTOM - MicroEmulatorActivity.config.CANVAS_AREA_TOP));
		}
	}

	public Image createImage(String name) throws IOException {
		Object midlet = MIDletBridge.getCurrentMIDlet();
		if (midlet == null) {
			midlet = getClass();
		}
		InputStream is = context.getResourceAsStream(midlet.getClass(), name);
		if (is == null) {
			throw new IOException(name + " could not be found.");
		}

		return createImage(is);
	}

	public Image createImage(Image source) {
		if (source.isMutable()) {
			return new AndroidImmutableImage((AndroidMutableImage) source);
		} else {
			return source;
		}
	}

	public Image createImage(InputStream is) throws IOException {
		byte[] imageBytes = new byte[1024];
		int num;
		ByteArrayOutputStream ba = new ByteArrayOutputStream();
		while ((num = is.read(imageBytes)) != -1) {
			ba.write(imageBytes, 0, num);
		}

		byte[] bytes = ba.toByteArray();
		return new AndroidImmutableImage(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
	}

	public Image createImage(int width, int height, boolean withAlpha, int fillColor) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException();
		}

		return new AndroidMutableImage(width, height, withAlpha, fillColor);
	}

	public Image createImage(byte[] imageData, int imageOffset, int imageLength) {
		return new AndroidImmutableImage(BitmapFactory.decodeByteArray(imageData, imageOffset, imageLength));
	}

	public Image createImage(Image image, int x, int y, int width, int height, int transform) {
		// TODO AndroidDisplayGraphics.drawRegion code is similar
		if (image == null)
			throw new NullPointerException();
		if (x + width > image.getWidth() || y + height > image.getHeight() || width <= 0 || height <= 0 || x < 0
				|| y < 0)
			throw new IllegalArgumentException("Area out of Image");

        Bitmap img;
        if (image.isMutable()) {
            img = ((AndroidMutableImage) image).getBitmap();
        } else {
            img = ((AndroidImmutableImage) image).getBitmap();
        }            

        Matrix matrix = new Matrix();
        switch (transform) {
        case Sprite.TRANS_NONE: {
            break;
        }
        case Sprite.TRANS_ROT90: {
        	matrix.preRotate(90);
            break;
        }
        case Sprite.TRANS_ROT180: {
            matrix.preRotate(180);
            break;
        }
        case Sprite.TRANS_ROT270: {
            matrix.preRotate(270);
            break;
        }
        case Sprite.TRANS_MIRROR: {
        	matrix.preScale(-1, 1);
            break;
        }
        case Sprite.TRANS_MIRROR_ROT90: {
        	matrix.preScale(-1, 1);
        	matrix.preRotate(-90);
            break;
        }
        case Sprite.TRANS_MIRROR_ROT180: {
        	matrix.preScale(-1, 1);
            matrix.preRotate(-180);
            break;
        }
        case Sprite.TRANS_MIRROR_ROT270: {
        	matrix.preScale(-1, 1);
            matrix.preRotate(-270);
            break;
        }
        default:
            throw new IllegalArgumentException("Bad transform");
        }

		return new AndroidImmutableImage(Bitmap.createBitmap(img, x, y, width, height, matrix, true));
	}

	public Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
		if (rgb == null)
			throw new NullPointerException();
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException();
		
		// TODO processAlpha is not handled natively, check whether we need to create copy of rgb
		int[] newrgb = rgb;
		if (!processAlpha) {
			newrgb = new int[rgb.length];
			for (int i = 0; i < rgb.length; i++) {
				newrgb[i] = (0x00ffffff & rgb[i]) | 0xff000000;
			}
		}
		return new AndroidImmutableImage(Bitmap.createBitmap(newrgb, width, height, Bitmap.Config.ARGB_8888));
	}

    public Graphics getGraphics(GameCanvas gameCanvas)
    {
        return ((AndroidCanvasUI) DisplayUtils.getDisplayableUI(gameCanvas)).getGraphics();
    }
    
    public void flushGraphics(GameCanvas gameCanvas, int x, int y, int width, int height) {
        AndroidCanvasUI ui = ((AndroidCanvasUI) DisplayUtils.getDisplayableUI(gameCanvas));
        CanvasView canvasView = (CanvasView) ui.getView();
        if (canvasView != null) {
            canvasView.flushGraphics(x, y, width, height);
        }
    }
    
    private Timer flashBackLightTimer = null;
    
    public boolean flashBacklight(int duration) {
    	if (flashBackLightTimer == null) {
    		flashBackLightTimer = new Timer();
    	}
    	
		PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
		final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "");
		wakeLock.acquire();
		
		flashBackLightTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				wakeLock.release();
			}
			
		}, duration);
		
    	return true;
    }

	public int getFullHeight() {
		return displayRectangleHeight;
	}

	public int getFullWidth() {
		return displayRectangleWidth;
	}

	public int getHeight() {
		// TODO Auto-generated method stub
		return displayRectangleHeight;
	}

	public int getWidth() {
		// TODO Auto-generated method stub
		return displayRectangleWidth;
	}

	public boolean isColor() {
		return true;
	}

	public boolean isFullScreenMode() {
		// TODO Auto-generated method stub
		return false;
	}

	public int numAlphaLevels() {
		return 256;
	}

	public int numColors() {
		return 65536;
	}

	public void repaint(int x, int y, int width, int height) {
		paintDisplayable(x, y, width, height);
	}

	public void setScrollDown(boolean arg0) {
		// TODO Auto-generated method stub

	}

	public void setScrollUp(boolean arg0) {
		// TODO Auto-generated method stub

	}
	
	public void addDisplayRepaintListener(DisplayRepaintListener listener) {
	    displayRepaintListeners.add(listener);
	}

    public void removeDisplayRepaintListener(DisplayRepaintListener listener) {
        displayRepaintListeners.remove(listener);
    }

	public void paintDisplayable(int x, int y, int width, int height) {
        rectangle.left = x;
        rectangle.top = y;
        rectangle.right = x + width;
        rectangle.bottom = y + height;
        for (int i = 0; i < displayRepaintListeners.size(); i++) {
            DisplayRepaintListener l = displayRepaintListeners.get(i);
            if (l != null) {
                l.repaintInvoked(rectangle);    
            }
        }
	}
	
}
