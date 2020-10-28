/*
 * $Id$
 *
 * Copyright (c) 2004-2005 by the TeXlapse Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package net.sourceforge.texlipse.wizards;

import java.awt.*;
import java.awt.event.*;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sourceforge.texlipse.TexlipsePlugin;
import net.sourceforge.texlipse.TTSIntegration.TTSConversion;
import net.sourceforge.texlipse.TTSIntegration.TTSProperties;
import net.sourceforge.texlipse.builder.BuilderChooser;
import net.sourceforge.texlipse.properties.TexlipseProperties;
import net.sourceforge.texlipse.templates.ProjectTemplateManager;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.fieldassist.AutoCompleteField;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 * Prefs-page on the project creation wizard.
 * 
 * @author kpkarlss
 */
public class TexlipseProjectCreationWizardPage extends TexlipseWizardPage {
	    
    // textfield for project name
//    private Text projectNameField;
    private Text projectNameField;
    
    // project location, if not in workspace
    private Text projectLocationField;
    
    // textfield for language
    private Text languageField;
    
    // textfield for template preview
    private Text descriptionField;
    
    // template chooser
	private List templateList;

	// template type (system/user)
	private Label typeLabel;

    // output format / build order chooser
    private BuilderChooser outputChooser;
    
    private String workspacePath;

    /**
     * 
     * @param attributes Project attributes
     */
    public TexlipseProjectCreationWizardPage(TexlipseProjectAttributes attributes) {
        super(0, attributes);
    }

    /**
     * Create the layout of the page.
     * @param parent parent component in the UI
     * @return number of components using a status message
     */
    
    public void createComponents(Composite parent) {
        
        // path to the workspace root directory
        workspacePath = ResourcesPlugin.getWorkspace().getRoot().getLocation().addTrailingSeparator().toOSString();
//        try {
//			TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_CREATE_PROJECT);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
        createProjectNameControl(parent);
        addSpacer(parent, 2);
        createProjectLocationControl(parent);
        addSpacer(parent, 2);
        createOutputFormatControl(parent);
        addSeparator(parent);
        addSpacer(parent, 2);
        createLabels(parent);
        createTemplateControl(parent);
        
       // speak("Enter Project Name");
        //this updates the template description area and the template type label
    	updateEntries();
    }

    /**
     * @param parent parent component
     */
    private void createOutputFormatControl(Composite parent) {
        outputChooser = new BuilderChooser(parent);
        GridData ngd = new GridData(GridData.FILL_HORIZONTAL);
        ngd.horizontalSpan = 2;
        outputChooser.getControl().setLayoutData(ngd);
        outputChooser.setSelectedBuilder(TexlipsePlugin.getDefault().getPreferenceStore().getInt(TexlipseProperties.BUILDER_NUMBER));
        String o = attributes.getOutputFile();
        attributes.setOutputFile(o.substring(0, o.lastIndexOf('.')+1) + outputChooser.getSelectedFormat());
         attributes.setOutputFormat(outputChooser.getSelectedFormat());
        
        outputChooser.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent event) {
                String o = attributes.getOutputFile();
                attributes.setOutputFile(o.substring(0, o.lastIndexOf('.')+1) + outputChooser.getSelectedFormat());
                attributes.setOutputFormat(outputChooser.getSelectedFormat());
                attributes.setBuilder(outputChooser.getSelectedBuilder());
            }});
    }

    /**
     * Create project name settings box.
     * @param composite the parent container
     */
    
    private void createProjectNameControl(Composite composite) {
        Composite c = new Composite(composite, SWT.NULL);
        c.setLayout(new GridLayout(2, false));
        GridData lgd = new GridData(GridData.FILL_HORIZONTAL);
        lgd.horizontalSpan = 2;
        c.setLayoutData(lgd); 

        // add label
        Label label = new Label(c, SWT.LEFT);
        label.setText(TexlipsePlugin.getResourceString("projectWizardNameLabel"));
        label.setToolTipText(TexlipsePlugin.getResourceString("projectWizardNameTooltip"));
        label.setLayoutData(new GridData());

        // add text field
        projectNameField = new Text(c, SWT.SINGLE | SWT.BORDER);
        projectNameField.setText(attributes.getProjectName());
        projectNameField.setToolTipText(TexlipsePlugin.getResourceString("projectWizardNameTooltip"));
        projectNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        // speak project name text 
        projectNameField.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                if (!projectNameField.isDisposed()) {
                    validateProjectName(projectNameField.getText());
                    //speak(projectNameField.getText());
                    if (!projectLocationField.isEnabled()) {
                        projectLocationField.setText(workspacePath + projectNameField.getText());
                        System.out.println(workspacePath + projectNameField.getText());
                    }
                }
            }
        });
        
//        projectNameField.setFocus(new );
//        projectNameField.addKeyListener(new KeyListener() {
//			@Override
//			public void keyReleased(org.eclipse.swt.events.KeyEvent e) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void keyPressed(org.eclipse.swt.events.KeyEvent e) {
//				// TODO Auto-generated method stub
//				if(e.keyCode == KeyEvent.VK_ENTER)
//				{
//					speak("Tabbed");
//				}
//			}
//		});
        
    //  projectNameField.setFocusTraversalKeysEnabled();
        
//      ChangeListener changeListener = new ChangeListener() {
//      	
//          public void stateChanged(ChangeEvent changeEvent) {
//            Text sourceTabbedPane = (Text) changeEvent.getSource();
//            sourceTabbedPane.addKeyListener(new KeyAdapter())
//            {
//          	  
//            });
//           // System.out.println("Tab changed to: " + sourceTabbedPane.getTitleAt(index));
//          }
//        };
        
//      projectNameField.addKeyListener(new KeyAdapter() {
//          public void keyPressed(KeyEvent e) {                
//             if(e.getKeyCode() == KeyEvent.VK_ENTER){
//             }
//          }        
//       });
//        projectNameField.addFocusListener(new FocusListener() {
//			
//			@Override
//			public void focusLost(FocusEvent e) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void focusGained(FocusEvent e) {
//				// TODO Auto-generated method stub
//				
//			}
//		});;
//        projectNameField..setFocusTraversalKeysEnabled(false);
       // projectNameField.addKeyListener(new KeyListener() {
        	
        	
			//@Override
//			public void keyReleased(org.eclipse.swt.events.KeyEvent e) {
//				// TODO Auto-generated method stub
//				//tabkey();
////				 robot.keyRelease(KeyEvent.VK_TAB);
////				if(e.getEventType() == KeyEvent.VK_TAB)
////				{
////				speak("The Two Letter (ISO 639 standard) language code en");
////				}
//			}
//			
//			@Override
//			public void keyPressed(org.eclipse.swt.events.KeyEvent e) {
//				// TODO Auto-generated method stub
////				int key = e.getKeyCode();
////				 if(e.keyCode == KeyEvent.VK_TAB)
//				
////				Robot robot = new Robot();
////		        robot.keyRelease(KeyEvent.VK_TAB);
////				if(eKeyEvent.VK_TAB)
////				{
////					 speak("The Two Letter (ISO 639 standard) language code en");
////	                     //   JOptionPane.showMessageDialog(null,"TAB PRESSED");
////	                }
//				
//			}
//		});
        
        projectNameField.addTraverseListener(new TraverseListener() {
			
			@Override
			public void keyTraversed(TraverseEvent e) {
				// TODO Auto-generated method stub
				if(e.keyCode == KeyEvent.VK_TAB)
				{
//					  try {
//						TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_ISO_STANDARD);
//					} catch (IOException e1) {
//						// TODO Auto-generated catch block
//						e1.printStackTrace();
//					}
					  

				}

			}
		});
        projectNameField.addFocusListener(new FocusListener() {
			
			@Override
			public void focusLost(FocusEvent e) {
				// TODO Auto-generated method stub
				//System.out.println("focus lost" + projectNameField.getText());
				//descriptionField.setFocus();
				
			}
			
			@Override
			public void focusGained(FocusEvent e) {
				// TODO Auto-generated method stub
			//	System.out.println("focus gained " + projectNameField.getText());
			}
		});
        
        // add language label
        final Label mainLabel = new Label(c, SWT.LEFT);
        mainLabel.setText(TexlipsePlugin.getResourceString("propertiesLanguage"));
        mainLabel.setToolTipText(TexlipsePlugin.getResourceString("propertiesLanguageDescription"));
        mainLabel.setLayoutData(new GridData());
        
        // add text field
        languageField = new Text(c, SWT.SINGLE | SWT.BORDER);
        languageField.setText(attributes.getLanguageCode());
        languageField.setToolTipText(TexlipsePlugin.getResourceString("propertiesLanguageDescription"));
        languageField.setLayoutData(new GridData());     
        new AutoCompleteField(languageField, new TextContentAdapter(), Locale.getISOLanguages());
        languageField.setTextLimit(2);
        //
        languageField.setEditable(false);
        
        languageField.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                if (!languageField.isDisposed()) {
                    String text = languageField.getText();
//                    speak("The two - letter (ISO 639 standard) language code " + text);
//                    try {
//						TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_TWO_LETTER_STANDARD);
//					} catch (IOException e1) {
//						// TODO Auto-generated catch block
//						e1.printStackTrace();
//					}
                    IStatus status = createStatus(IStatus.OK, "");
                    //Has it exactly two characterssetControlContents
                    //Is this a valid language code
                    if (text.length() != 2 || Arrays.binarySearch(Locale.getISOLanguages(), text) < 0) {
                        status = createStatus(IStatus.WARNING,
                            TexlipsePlugin.getResourceString("projectWizardLanguageCodeError"));
                    }
                    attributes.setLanguageCode(text);
                    updateStatus(status, languageField);
                }
            }
        });
//        languageField.addTraverseListener(new TraverseListener() {
//			
//			@Override
//			public void keyTraversed(TraverseEvent e) {
//				// TODO Auto-generated method stub
//				if(e.keyCode == KeyEvent.VK_TAB)
//				{
//				try {
//					TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_SELECTED_PROJECT);
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
//			}}
//		});
    }

    /**
     * Create project name settings box.
     * @param composite the parent container
     */
    private void createProjectLocationControl(Composite parent) {

        // create borders
        Group group = new Group(parent, SWT.NULL);
        group.setText(TexlipsePlugin.getResourceString("projectWizardLocationTitle"));
        group.setLayout(new GridLayout());
        GridData lgd = new GridData(GridData.FILL_HORIZONTAL);
        lgd.horizontalSpan = 2;
        group.setLayoutData(lgd);
        
        // add radioButtons
        final Button createLocalProjectButton = new Button(group, SWT.RADIO | SWT.LEFT);
        createLocalProjectButton.setLayoutData(new GridData());
        createLocalProjectButton.setText(TexlipsePlugin.getResourceString("projectWizardLocationLocal"));
        createLocalProjectButton.setSelection(true);
        createLocalProjectButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent event) {
                //Only execute if this field becomes selected
                if (!createLocalProjectButton.getSelection()) return;
                Control[] c = projectLocationField.getParent().getChildren();
                for (int i = 0; i < c.length; i++) {
                    c[i].setEnabled(false);
                }
                projectLocationField.setText(workspacePath + projectNameField.getText());
//                try {
//					TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_SELECTED_PROJ);
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
                attributes.setProjectLocation(null);
                updateStatus(createStatus(IStatus.OK, ""), projectLocationField);
            }});
        
        
//        createLocalProjectButton.addTraverseListener(new TraverseListener() {
//			
//			@Override
//			public void keyTraversed(TraverseEvent e) {
//				// TODO Auto-generated method stub
//				//speak("Out put format is pdf selected \n Build commands is pdf latex.exe selected \n. Focus on setup build tools");
//				if(e.keyCode == KeyEvent.VK_TAB)
//				{
//				try {
//					TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_FOCUS);
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
//			}}
//		});
        
        final Button createExternalProjectButton = new Button(group, SWT.RADIO | SWT.LEFT);
        createExternalProjectButton.setLayoutData(new GridData());
        createExternalProjectButton.setText(TexlipsePlugin.getResourceString("projectWizardLocationExternal"));
        createExternalProjectButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent event) {
                //Only execute if this field becomes selected
                if (!createExternalProjectButton.getSelection()) return;
                Control[] c = projectLocationField.getParent().getChildren();
//                try {
//					TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_EXTERNAL_LOCATION);
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
                for (int i = 0; i < c.length; i++) {
                    c[i].setEnabled(true);
                }
                projectLocationField.setFocus();
                projectLocationField.setText("");
            }});
        // disable control 
     //   createExternalProjectButton.setEnabled(false);
//        createExternalProjectButton.addTraverseListener(new TraverseListener() {
//			
//			@Override
//			public void keyTraversed(TraverseEvent e) {
//				// TODO Auto-generated method stub
//				if(e.keyCode == KeyEvent.VK_TAB)
//				{
//				try {
//					TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_FOCUS_TOOL);
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
//			}}
//		});
        
        Composite composite = new Composite(group, SWT.NULL);
        composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout cgl = new GridLayout();
        cgl.numColumns = 3;
        composite.setLayout(cgl);
        
        // add location label
        Label label = new Label(composite, SWT.LEFT);
        label.setText(TexlipsePlugin.getResourceString("projectWizardLocationLabel"));
        label.setLayoutData(new GridData());
        label.setEnabled(false);
        
        // add location field
        projectLocationField = new Text(composite, SWT.SINGLE | SWT.BORDER);
        projectLocationField.setText(workspacePath);
        projectLocationField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        projectLocationField.setEnabled(false);
        projectLocationField.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                if (!projectLocationField.isDisposed() && projectLocationField.isEnabled()) {
                    validateProjectLocation(projectLocationField.getText());
                }
            }});
        
        Button browseLocationButton = new Button(composite, SWT.PUSH);
        browseLocationButton.setText(TexlipsePlugin.getResourceString("openBrowse"));
        browseLocationButton.setLayoutData(new GridData());
        browseLocationButton.setEnabled(false);
        browseLocationButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent event) {
                DirectoryDialog dialog = new DirectoryDialog(projectLocationField.getShell());
                dialog.setMessage(TexlipsePlugin.getResourceString("projectWizardLocationSelect"));
                dialog.setText(TexlipsePlugin.getResourceString("projectWizardLocationSelect"));
                String current = projectLocationField.getText();
                if (current == null || current.length() == 0) {
                    current = workspacePath;
                }
                dialog.setFilterPath(current);
                String dirStr = dialog.open();
                if (dirStr != null) {
                    File dir = new File(dirStr);
                    if (dir.exists() && dir.isDirectory()) {
                        projectLocationField.setText(dir.getAbsolutePath());
                    }
                }
            }});
    }

    /**
     * Check if the external project location is valid.
     * This method updates the status message for the project location.
     * @param text the path to project location
     */
    private void validateProjectLocation(String text) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        String pName = projectNameField.getText();
        //Project needs a name, otherwise an exception is thrown. The name is not important for path checking
        if ("".equals(pName)) pName = "fdslajflj";
        IProject p = workspace.getRoot().getProject(pName);
        IStatus status = workspace.validateProjectLocation(p, new Path(text));
        if (status.getSeverity() == IStatus.OK) {
            attributes.setProjectLocation(text);
        }
        updateStatus(status, projectLocationField);
    }
    
    /**
     * Creates labels for the template list and the description text area 
     * to the given Composite object
     * 
     * @param composite the parent container
     */
    private void createLabels(Composite composite) {
    	
        // add label for the list of templates
        Label label = new Label(composite, SWT.LEFT);
        label.setText(TexlipsePlugin.getResourceString("projectWizardTemplateListLabel"));
        label.setToolTipText(TexlipsePlugin.getResourceString("projectWizardTemplateListTooltip"));
        label.setLayoutData(new GridData());
    	
        // add composite containing a label for the description area and templatetype (system/user) label
        Composite c = new Composite(composite, SWT.NONE);
        c.setLayout(new GridLayout(2,false));
        c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        // add label for the description area
        Label l = new Label(c, SWT.LEFT);
        l.setText(TexlipsePlugin.getResourceString("projectWizardTemplateDescriptionLabel"));
        l.setToolTipText(TexlipsePlugin.getResourceString("projectWizardTemplateDescriptionTooltip"));        
    	l.setLayoutData(new GridData());
       
        // add label for presenting the type of the selected template
    	typeLabel = new Label(c, SWT.RIGHT);
    	//Note that thetext of this label is set by updateEntries()
    	typeLabel.setToolTipText(TexlipsePlugin.getResourceString("projectWizardTemplateTypeTooltip"));
        typeLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    }
    
    
    /**
     * Creates a list element containing available templates (system and user)
     * and a text area next to it for showing description about the selected template
     * 
     * @param composite the parent container
     */
    private void createTemplateControl(Composite composite) {
        // add list for templates
        templateList = new List(composite, SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		templateList.setItems(ProjectTemplateManager.loadTemplateNames());
        templateList.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        templateList.setToolTipText(TexlipsePlugin.getResourceString("projectWizardTemplateTooltip"));
        templateList.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
            	attributes.setTemplate(templateList.getSelection()[0]);
            	updateEntries();
            }});
        
        templateList.setSelection(0);
//        templateList.addTraverseListener(new TraverseListener() {
//			
//			@Override
//			public void keyTraversed(TraverseEvent e) {
//				// TODO Auto-generated method stub
//				if(e.keyCode == KeyEvent.VK_TAB)
//				{
//				try {
//					TTSConversion.getDefault().speak(TTSProperties.MSG_TEMPLATE_TEXT);
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
//			}
//			}
//		});
        // this has to be done, because setSelection() doesn't generate an event
        attributes.setTemplate(templateList.getItem(0));
        
        // add TextField for the selected template's description
        descriptionField = new Text(composite, SWT.MULTI | SWT.BORDER);
        descriptionField.setToolTipText(TexlipsePlugin.getResourceString("projectWizardTemplateDescriptionTooltip"));
        descriptionField.setLayoutData(new GridData(GridData.FILL_BOTH));
        descriptionField.setEditable(false);
        
        descriptionField.addTraverseListener(new TraverseListener() {
			
			@Override
			public void keyTraversed(TraverseEvent e) {
				
				// TODO Auto-generated method stub
				if(e.keyCode == KeyEvent.VK_TAB)
				{
				if(projectNameField.getText()=="")
				{
					//button cancel speak
//					try {
//						TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_CANCEL);
//					} catch (IOException e1) {
//						// TODO Auto-generated catch block
//						e1.printStackTrace();
//					}
				}
				else
				{
					//button next speak
//					try {
//						TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_NEXT_);
//					} catch (IOException e1) {
//						// TODO Auto-generated catch block
//						e1.printStackTrace();
//					}
				}
			}
			}
		});
    }

    /**
     * Updates the description text area and template type label 
     * 
     */
    private void updateEntries(){
    	typeLabel.setText(templateType(attributes.getTemplate()));
    	readProjectTemplateDescription(attributes.getTemplate());
    }

    /**
     * Returns the type of the given template.
     * 
     * @param template name of a template (should be the selected item from 
     *   the list element containing all available templates).
     * @return Either "User defined template" or "System defined template" depending
     *   wether the given template is found in plugins templates directory (former if not,
     *   latter if yes).
     */
    private String templateType(String template){
    	URL templateUrl = TexlipsePlugin.getDefault().getBundle().getEntry("templates" + File.separator + template + ".tex");
    	if(templateUrl==null) return "User defined template";
    	else return "System template";  
    }

    /**
     * Starts to read the given template (assuming it is found
     * from either plugins template directory or user's template directory...
     * if not, the description is set to "") and puts all lines
     * starting with "%%" (excluding "%%") to the description text area.
     * Reading is ended, when a line not starting wiht "%%" is encountered
     * or the end of file is reached.
     *  
     * @param template name of a template
     */
    
    private void readProjectTemplateDescription(String template) {
    	//if system template, then it is found here
    	//shaban
    	// template name save in string 
//        System.out.println(template);
       // speak("Template Article is selected.");
    	URL templateUrl = TexlipsePlugin.getDefault().getBundle().getEntry("templates" + File.separator + template + ".tex");
        String userTemplate = null;
    	if (templateUrl==null)
    	{
    		//if not, then the template is user defined 
    		File userTemplateFolder = ProjectTemplateManager.getUserTemplateFolder();
    		userTemplate = userTemplateFolder.getAbsolutePath()+File.separator+template+".tex";
    	}
         
    	try { 
    		String line = null;
    		
			BufferedReader r;
			if(templateUrl!=null) r = new BufferedReader(new InputStreamReader(templateUrl.openStream()));
			else r = new BufferedReader(new FileReader(userTemplate));
			StringBuffer sb = new StringBuffer();
			
			while ((line = r.readLine()) != null) {
				if(!line.startsWith("%%")) break;
				if(line.length()>2) sb.append(line.substring(2));
				sb.append('\n');
				
				
			}
			TTSProperties.MSG_TEMPLATE_TEXT = sb.toString();
			r.close();
			sb.toString();       
	        descriptionField.setText(sb.toString());
    	} catch (IOException e) {
    		TexlipsePlugin.log("Reading a description of template file:", e);
    		descriptionField.setText("");
		}
    }

    /**
     * Update the status line when the page becomes visible.
     */
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            validateProjectName(projectNameField.getText());
        }
    }

    /**
     * Check if there already is a project under the given name.
     * @param text
     */
    private void validateProjectName(String text) {
        
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IStatus status = workspace.validateName(text, IResource.PROJECT);
        
        // Shaban
        String str =  text;
        if(str.length() ==0)
        {
        	try {
				TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_ENTER_PROJ_NAME+ str);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        else if(str.length() >=1)
        {
        str = text.substring(text.length()-1); 
        try {
			TTSConversion.getDefault().speak(str);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
        }
//        	speak(str);
        	
        if (status.isOK()) {
            if (workspace.getRoot().getProject(text).exists()) {
                status = createStatus(IStatus.ERROR,
                        TexlipsePlugin.getResourceString("projectWizardNameError"));
            }
            attributes.setProjectName(text);
        }

        updateStatus(status, projectNameField);
    }
}
