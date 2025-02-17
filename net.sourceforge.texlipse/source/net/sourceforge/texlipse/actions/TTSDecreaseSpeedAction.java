package net.sourceforge.texlipse.actions;

import java.io.IOException;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import net.sourceforge.texlipse.TTSIntegration.CapsLock;
import net.sourceforge.texlipse.TTSIntegration.TTSConversion;
import net.sourceforge.texlipse.TTSIntegration.TTSProperties;

public class TTSDecreaseSpeedAction implements IEditorActionDelegate {

	private ITextEditor textEditor;
	
	public TTSDecreaseSpeedAction() {

	}
	
	@Override
	public void run(IAction action) {
		// TODO Auto-generated method stub
		if(textEditor == null)
			return;	
		//setLastSpeed(getLastSpeed() + 1);
		try {
			TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_DECREASE_SPEED);
			//Disable caps lock when shorkey pressed  
			CapsLock.disableCapsLock();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setActiveEditor(IAction action, IEditorPart targetEditor) {
		if (targetEditor instanceof ITextEditor) {
			this.textEditor = (ITextEditor) targetEditor;
		}
		
	}


}
