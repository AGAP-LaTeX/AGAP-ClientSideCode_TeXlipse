package net.sourceforge.texlipse.TTSIntegration;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;

import net.sourceforge.texlipse.editor.TexEditor;

import java.awt.AWTException;
import java.awt.Robot;

public class  CapsLock {
	public static void disableCapsLock()
	{
		try {
			Thread.sleep(1000);
			TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_CHK_CAPSLOCK);
		} catch (InterruptedException | IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
}
