/*
 * $Id$
 *
 * Copyright (c) 2004-2005 by the TeXlapse Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package net.sourceforge.texlipse.texparser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IMarker;

import net.sourceforge.texlipse.TexlipsePlugin;
import net.sourceforge.texlipse.TTSIntegration.TTSConversion;
import net.sourceforge.texlipse.TTSIntegration.TTSProperties;
import net.sourceforge.texlipse.actions.PDFAccessibility;
import net.sourceforge.texlipse.builder.AbstractProgramRunner;
import net.sourceforge.texlipse.model.DocumentReference;
import net.sourceforge.texlipse.model.OutlineNode;
import net.sourceforge.texlipse.model.ParseErrorMessage;
import net.sourceforge.texlipse.model.ReferenceEntry;
import net.sourceforge.texlipse.model.TexCommandEntry;
import net.sourceforge.texlipse.texparser.lexer.LexerException;
import net.sourceforge.texlipse.texparser.node.EOF;
import net.sourceforge.texlipse.texparser.node.TArgument;
import net.sourceforge.texlipse.texparser.node.TCbegin;
import net.sourceforge.texlipse.texparser.node.TCbib;
import net.sourceforge.texlipse.texparser.node.TCbibstyle;
import net.sourceforge.texlipse.texparser.node.TCchapter;
import net.sourceforge.texlipse.texparser.node.TCcite;
import net.sourceforge.texlipse.texparser.node.TCend;
import net.sourceforge.texlipse.texparser.node.TCinclude;
import net.sourceforge.texlipse.texparser.node.TCinput;
import net.sourceforge.texlipse.texparser.node.TClabel;
import net.sourceforge.texlipse.texparser.node.TCnew;
import net.sourceforge.texlipse.texparser.node.TCommentline;
import net.sourceforge.texlipse.texparser.node.TCpackage;
import net.sourceforge.texlipse.texparser.node.TCparagraph;
import net.sourceforge.texlipse.texparser.node.TCpart;
import net.sourceforge.texlipse.texparser.node.TCpbib;
import net.sourceforge.texlipse.texparser.node.TCpindex;
import net.sourceforge.texlipse.texparser.node.TCref;
import net.sourceforge.texlipse.texparser.node.TCsection;
import net.sourceforge.texlipse.texparser.node.TCssection;
import net.sourceforge.texlipse.texparser.node.TCsssection;
import net.sourceforge.texlipse.texparser.node.TCword;
import net.sourceforge.texlipse.texparser.node.TLBrace;
import net.sourceforge.texlipse.texparser.node.TOptargument;
import net.sourceforge.texlipse.texparser.node.TRBrace;
import net.sourceforge.texlipse.texparser.node.TStar;
import net.sourceforge.texlipse.texparser.node.TTaskcomment;
import net.sourceforge.texlipse.texparser.node.TVtext;
import net.sourceforge.texlipse.texparser.node.TWhitespace;
import net.sourceforge.texlipse.texparser.node.Token;

/**
 * Simple parser for LaTeX: does very basic structure checking and extracts
 * useful data.
 * 
 * @author Oskar Ojala
 * @author Boris von Loesch
 */
public class LatexParser {

	// These should be allocated between 1000-2000
	public static final int TYPE_LABEL = 1000;

	private static final Pattern PART_RE = Pattern.compile("\\\\part(?:[^a-zA-Z]|$)");
	private static final Pattern CHAPTER_RE = Pattern.compile("\\\\chapter(?:[^a-zA-Z]|$)");
	private static final Pattern SECTION_RE = Pattern.compile("\\\\section(?:[^a-zA-Z]|$)");
	private static final Pattern SSECTION_RE = Pattern.compile("\\\\subsection(?:[^a-zA-Z]|$)");
	private static final Pattern SSSECTION_RE = Pattern.compile("\\\\subsubsection(?:[^a-zA-Z]|$)");
	private static final Pattern PARAGRAPH_RE = Pattern.compile("\\\\paragraph(?:[^a-zA-Z]|$)");
	private static final Pattern LABEL_RE = Pattern.compile("\\\\label(?:[^a-zA-Z]|$)");
	public static boolean IsWarning = false;
	public static boolean IsAccessibilityPackage=false;

	public static List<String> definedCommandsList = new ArrayList<String>();

	/**
	 * Defines a new stack implementation, which is unsynchronized and tuned for
	 * the needs of the parser, making it much faster than java.util.Stack
	 * 
	 * @author Oskar Ojala
	 */
	private final static class StackUnsynch<E> {

		private static final int INITIAL_SIZE = 10;
		private static final int GROWTH_FACTOR = 2;
		private int capacity;
		private int size;
		private Object[] stack;

		/**
		 * Creates a new stack.
		 */
		public StackUnsynch() {
			stack = new Object[INITIAL_SIZE];
			size = 0;
			capacity = INITIAL_SIZE;
		}

		/**
		 * @return True if the stack is empty, false if it contains items
		 */
		public boolean empty() {
			return (size == 0);
		}

		/**
		 * @return The item at the top of the stack
		 */
		@SuppressWarnings("unchecked")
		public E peek() {
			return (E) (stack[size - 1]);
		}

		/**
		 * Removes the item at the stop of the stack.
		 * 
		 * @return The item at the top of the stack
		 */
		@SuppressWarnings("unchecked")
		public E pop() {
			size--;
			E top = (E) stack[size];
			stack[size] = null;
			return top;
		}

		/**
		 * Pushes an item to the top of the stack.
		 * 
		 * @param item
		 *            The item to push on the stack
		 */
		public void push(final E item) {
			// what if size would be where to put the next item?
			if (size >= capacity) {
				capacity *= GROWTH_FACTOR;
				Object[] newStack = new Object[capacity];
				System.arraycopy(stack, 0, newStack, 0, stack.length);
				stack = newStack;
			}
			stack[size] = item;
			size++;
		}

		/**
		 * Clears the stack; removes all entries.
		 */
		public void clear() {
			for (size--; size >= 0; size--) {
				stack[size] = null;
			}
			size = 0;
		}
	}

	private List<ReferenceEntry> labels;
	private List<DocumentReference> cites;
	private List<DocumentReference> refs;
	private ArrayList<TexCommandEntry> commands;
	private List<ParseErrorMessage> tasks;

	private List<String> bibs;
	private String bibstyle;

	private List<OutlineNode> inputs;

	private ArrayList<OutlineNode> outlineTree;

	private List<ParseErrorMessage> errors;
	private static List<ParseErrorMessage> errorsStatic;

	private OutlineNode documentEnv;

	private boolean biblatexMode;
	private String biblatexBackend;
	private boolean localBib;
	private boolean index;
	private boolean fatalErrors;

	/**
	 * Initializes the internal datastructures that are exported after parsing.
	 */
	private void initializeDatastructs() {
		
		this.errors = new ArrayList<ParseErrorMessage>();
		this.labels = new ArrayList<ReferenceEntry>();
		this.cites = new ArrayList<DocumentReference>();
		this.refs = new ArrayList<DocumentReference>();
		this.commands = new ArrayList<TexCommandEntry>();
		this.tasks = new ArrayList<ParseErrorMessage>();

		this.inputs = new ArrayList<OutlineNode>(2);

		this.outlineTree = new ArrayList<OutlineNode>();

		this.bibs = new ArrayList<String>();
		this.biblatexMode = false;
		this.biblatexBackend = null;
		this.localBib = false;
		this.index = false;
		this.fatalErrors = false;
	}

	/**
	 * Adds a node for a document section to the outline tree.
	 *
	 * @param blocks
	 *            stack with open blocks in outline
	 * @param envBlocks
	 *            stack with open environments
	 * @param startLine
	 *            beginning line of this section
	 * @param level
	 *            node document hierarchy level
	 * @param text
	 *            node text
	 * @return the document hierarchy level of this node's parent
	 */
	private int addSectionNode(final StackUnsynch<OutlineNode> blocks, final StackUnsynch<OutlineNode> envBlocks,
			final int startLine, final int level, final String text) {
		int parentLevel = OutlineNode.TYPE_DOCUMENT;

		OutlineNode on = new OutlineNode(text, level, startLine, null);

		if (!blocks.empty()) {
			boolean traversing = true;
			while (traversing && !blocks.empty()) {
				OutlineNode prev = blocks.peek();
				int prevType = prev.getType();
				if (prevType == OutlineNode.TYPE_ENVIRONMENT) {
					/*
					 * An environment is breaking the current section. Determine
					 * next parent which is not an environment, but an actual
					 * document hierarchy node
					 */
					OutlineNode topEnv; // First environment on top of previous
										// section
					OutlineNode parent; // Previous section
					do {
						/*
						 * Close environments in hierarchy. Blocks will be
						 * synchronized later again using envBlocks.
						 */
						topEnv = blocks.pop();
						if (!blocks.empty()) {
							parent = blocks.peek();
							prevType = parent.getType();
						} else {
							parent = null;
							prevType = OutlineNode.TYPE_DOCUMENT;
						}
					} while (prevType == OutlineNode.TYPE_ENVIRONMENT);

					/*
					 * If the previous document hierarchy level is deeper than
					 * this one, move the environment one document level up.
					 */
					int upperLevel = level - 1;
					if (prevType > upperLevel) {
						// Remove environment from current parent
						parent.deleteChild(topEnv);
						// Find next higher hierarchy level, if any...
						int envBegin = topEnv.getBeginLine();
						do {
							// Close blocks
							parent.setEndLine(envBegin);
							blocks.pop();
							parent = parent.getParent();
						} while (parent != null && parent.getType() > upperLevel);
						// ...and add there, or to tree root
						if (parent != null) {
							prevType = parent.getType();
							parent.addChild(topEnv);
						} else {
							prevType = OutlineNode.TYPE_DOCUMENT;
							outlineTree.add(topEnv);
						}
						topEnv.setParent(parent);
					}
					prev = parent;
				}
				if (prevType >= OutlineNode.TYPE_DOCUMENT && prevType < level) {
					parentLevel = prevType;
					if (prev != null) {
						prev.addChild(on);
					}
					on.setParent(prev);
					traversing = false;
				} else {
					prev.setEndLine(startLine);
					blocks.pop();
				}
			}
		}
		// add directly to tree if no parent was found
		if (blocks.empty()) {
			outlineTree.add(on);
		}
		blocks.push(on);
		return parentLevel;
	}

	/**
	 * Evaluates package loading options for biblatex and locates the backend
	 * option.
	 *
	 * @param options
	 *            string with options in format <code>key=value</code>, or
	 *            simply <code>key</code>, each separated by commas
	 * @return selected biblatex backend, if it was selected; otherwise null
	 */
	private static String findBiblatexBackend(String options) {
		int beIdx = options.indexOf("backend=");
		if (beIdx >= 0) {
			int startIdx = beIdx + 8; // move forward by length of "backend="
			int endIdx = options.indexOf(',', startIdx);
			if (endIdx > startIdx) {
				return options.substring(startIdx, endIdx).trim();
			} else if (endIdx == -1) {
				return options.substring(startIdx).trim();
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Parses a LaTeX document. Uses the given lexer's <code>next()</code>
	 * method to receive tokens that are processed.
	 * 
	 * @param lex
	 *            The lexer to use for extracting the document tokens
	 * @param definedLabels
	 *            Labels that are defined, used to check for references to
	 *            nonexistant labels
	 * @param definedBibs
	 *            Defined bibliography entries, used to check for references to
	 *            nonexistant bibliography entries
	 * @throws LexerException
	 *             If the given lexer cannot tokenize the document
	 * @throws IOException
	 *             If the document is unreadable
	 */
	public void parse(LatexLexer lex, boolean checkForMissingSections) throws LexerException, IOException {
		parse(lex, null, checkForMissingSections, null);
	}

	/**
	 * Parses a LaTeX document. Uses the given lexer's <code>next()</code>
	 * method to receive tokens that are processed.
	 * 
	 * @param lexer
	 *            The lexer to use for extracting the document tokens
	 * @param definedLabels
	 *            Labels that are defined, used to check for references to
	 *            nonexistant labels
	 * @param definedBibs
	 *            Defined bibliography entries, used to check for references to
	 *            nonexistant bibliography entries
	 * @param preamble
	 *            An <code>OutlineNode</code> containing the preamble, null if
	 *            there is no preamble
	 * @param checkForMissingSections
	 * @throws LexerException
	 *             If the given lexer cannot tokenize the document
	 * @throws IOException
	 *             If the document is unreadable
	 */

	public void parse(final LatexLexer lexer, final OutlineNode preamble, final boolean checkForMissingSections,
			String input) throws LexerException, IOException {
		initializeDatastructs();
		StackUnsynch<OutlineNode> blocks = new StackUnsynch<OutlineNode>();
		StackUnsynch<OutlineNode> envBlocks = new StackUnsynch<OutlineNode>();
		StackUnsynch<Token> braces = new StackUnsynch<Token>();

		boolean expectArg = false;
		boolean expectArg2 = false;
		boolean captionFound = false;
		boolean altFound = false;
		Token prevToken = null; 

		String packageOptions = null;

		TexCommandEntry currentCommand = null;
		int argCount = 0;
		int nodeType;

		Map<String, Integer> sectioning = new HashMap<String, Integer>();
        Set<String> fontsBlockedPackages = new HashSet<String>();
        Set<String> trackChangesBlockedPackages = new HashSet<String>();
        Set<String> waterMarkBlockedPackages = new HashSet<String>();
        Set<String> commentBlockedPackages = new HashSet<String>();
        Set<String> AccesibilitPackage = new HashSet<String>();
        addFontsBlockedPackagesToSet(fontsBlockedPackages);
        addTrackChangesBlockedPackagesToSet(trackChangesBlockedPackages);
        addWaterMarkBlockedPackagesToSet(waterMarkBlockedPackages);
        addCommentBlockedPackagesToSet(commentBlockedPackages);
        addAccessibilityPackagesToSet(AccesibilitPackage);
        

		// addBlockedPackagesToFontsSet'
		boolean IsTable = false;
		// int i =0;

		if (preamble != null) {
			outlineTree.add(preamble);
			blocks.push(preamble);
		}

		// newcommand would need to check for the valid format
		// duplicate labels?
		// change order of ifs to optimize performance?

		int accumulatedLength = 0;
		Token t = lexer.next();
		// PDFAccessibility.setPDFFullyAccessible(false); //reset flag before
		// parsing
		String TempText = "";
		String strTabular = "";
		boolean IsLongTable = false;
		boolean IsTableRowhead = false;

		for (; !(t instanceof EOF); t = lexer.next()) {
			// if(t.getText().contains("watermark")){
			if (PDFAccessibility.isPDFAccessibilityModeOn()) {
				if (IsTable || IsLongTable) {

					if (t.getText().contains("\\endhead")) {
						IsTableRowhead = true;
					}

					if (t.getText().contains("\\hline") || t.getText().contains("\\end")
							|| t.getText().contains("tabular") || t.getText().contains("|c")
							|| t.getText().contains("longtable")) {

					} else {
						if (t.getText().contains("\\\\")) {
							strTabular = strTabular + "&";
						}

						else {
							strTabular = strTabular + t.getText();
						}
					}
					if (t.getText().contains("tabular")) {
						errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
								TTSProperties.MSG_WARNING_ACESBLTY_LONG_TABLE, IMarker.SEVERITY_WARNING));
						TTSConversion.getDefault().speak(TTSProperties.MSG_WARNING_ACESBLTY_LONG_TABLE);

						String array[] = strTabular.split("&");
						for (int i = 0; i < array.length - 1; i++) {
							String strr = array[i];
							if (strr.trim().isEmpty()) {
								// System.err.println("showwarning");
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_BLANK_CELL,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_NEXT);
								try {
									Thread.sleep(500);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								TTSConversion.getDefault()
										.speak(TTSProperties.MSG_WARNING_ACESBLTY_BLANK_CELL);
								// PDFAccessibility.setPDFFullyAccessible(false);
							}
						}

					} else if (t.getText().contains("longtable")) {

						String array[] = strTabular.split("&");
						for (int i = 0; i < array.length - 1; i++) {
							String strr = array[i];
							if (strr.trim().isEmpty()) {
								// System.err.println("showwarning");
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_BLANK_CELL,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault()
										.speak(TTSProperties.MSG_WARNING_ACESBLTY_BLANK_CELL);
								// PDFAccessibility.setPDFFullyAccessible(false);
							}

							if (!IsTableRowhead) {
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_MISNG_ROWHEADER,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault()
										.speak(TTSProperties.MSG_WARNING_ACESBLTY_MISNG_ROWHEADER);
							}

						}

					}
				}
			}

			if (expectArg) {
				if (t instanceof TArgument) {
					if (prevToken instanceof TClabel) {
						// this.labels.add(new ReferenceEntry(t.getText()));
						ReferenceEntry l = new ReferenceEntry(t.getText());
						l.setPosition(t.getPos(), t.getText().length());
						l.startLine = t.getLine();
						this.labels.add(l);

						OutlineNode on = new OutlineNode(t.getText(), OutlineNode.TYPE_LABEL, t.getLine(), t.getPos(),
								t.getText().length());
						on.setEndLine(t.getLine());

						if (!blocks.empty()) {
							OutlineNode prev = blocks.peek();
							prev.addChild(on);
							on.setParent(prev);
						} else {
							outlineTree.add(on);
						}

					} else if (prevToken instanceof TCref) {
						this.refs
								.add(new DocumentReference(t.getText(), t.getLine(), t.getPos(), t.getText().length()));
					} else if (prevToken instanceof TCcite) {
						if (!"*".equals(t.getText())) {
							String[] cs = t.getText().split(",");
							for (String c : cs) {
								// just add all citation and check for errors
								// later, after updating the citation index
								this.cites.add(
										new DocumentReference(c.trim(), t.getLine(), t.getPos(), t.getText().length()));
							}
						}

					} else if (prevToken instanceof TCbegin) { // \begin{...}
						// here code tabular
						if (t.getText().contains("tabular")) {
							IsTable = true;
						} else if (t.getText().contains("longtable")) {
							IsLongTable = true;
						}

						OutlineNode on = new OutlineNode(t.getText(), OutlineNode.TYPE_ENVIRONMENT, t.getLine(),
								prevToken.getPos(),
								prevToken.getText().length() + accumulatedLength + t.getText().length());

						if ("document".equals(t.getText())) {
							if (preamble != null)
								preamble.setEndLine(t.getLine());
							blocks.clear();
							documentEnv = on;
						} else {
							if (!blocks.empty()) {
								OutlineNode prev = blocks.peek();
								prev.addChild(on);
								on.setParent(prev);
							} else {
								outlineTree.add(on);
							}
							blocks.push(on);
							envBlocks.push(on);
						}

					} else if (prevToken instanceof TCend) { // \end{...}
						int endLine = t.getLine();
						OutlineNode prev = null;

						// check if the document ends
						if ("document".equals(t.getText())) {
							documentEnv.setEndLine(endLine + 1);

							// terminate open blocks here; check for errors
							while (!blocks.empty()) {
								prev = blocks.pop();
								prev.setEndLine(endLine);
								if (prev.getType() == OutlineNode.TYPE_ENVIRONMENT) {
									errors.add(new ParseErrorMessage(prevToken.getLine(), prevToken.getPos(),
											prevToken.getText().length() + accumulatedLength + t.getText().length(),
											"\\end{" + prev.getName()
													+ "} expected, but \\end{document} found; at least one unbalanced begin-end",
											IMarker.SEVERITY_ERROR));
									fatalErrors = true;
								}
							}
						} else {
							// the "normal" case
							if (!envBlocks.empty()) {
								prev = envBlocks.pop();
								prev.setEndLine(endLine + 1);
								if (!prev.getName().equals(t.getText())) {
									fatalErrors = true;
									errors.add(new ParseErrorMessage(prev.getBeginLine(), prev.getOffsetOnLine(),
											prev.getDeclarationLength(),
											"\\end{" + prev.getName() + "} expected, but \\end{" + t.getText()
													+ "} found; unbalanced begin-end",
											IMarker.SEVERITY_ERROR));
									errors.add(new ParseErrorMessage(prevToken.getLine(), prevToken.getPos(),
											prevToken.getText().length() + accumulatedLength + t.getText().length(),
											"\\end{" + prev.getName() + "} expected, but \\end{" + t.getText()
													+ "} found; unbalanced begin-end",
											IMarker.SEVERITY_ERROR));
								}
								if (!blocks.empty() && blocks.peek().getType() == OutlineNode.TYPE_ENVIRONMENT) {
									blocks.pop();
								}
							} else {
								fatalErrors = true;
								errors.add(new ParseErrorMessage(prevToken.getLine(), prevToken.getPos(),
										prevToken.getText().length() + accumulatedLength + t.getText().length(),
										"\\end{" + t.getText() + "} found with no preceding \\begin",
										IMarker.SEVERITY_ERROR));
							}
						}

						if (PDFAccessibility.isPDFAccessibilityModeOn()) {
							if ("figure".equals(t.getText()) && false == captionFound) {
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_CAPTION,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault().speak(
										TTSProperties.MSG_WARNING_ACESBLTY_CAPTION);
								// PDFAccessibility.setPDFFullyAccessible(false);
								IsWarning = true;
							} else if ("figure".equals(t.getText()) && true == captionFound) {
								captionFound = false; // reset it to false
							}
							if ("figure".equals(t.getText()) && false == altFound) {
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_ALT,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault().speak(
										TTSProperties.MSG_WARNING_ACESBLTY_ALT);
								// PDFAccessibility.setPDFFullyAccessible(false);
								IsWarning = true;
							} else if ("figure".equals(t.getText()) && true == altFound) {
								altFound = false; // reset it to false
							}
						}
					} else if (prevToken instanceof TCpart) {
						addSectionNode(blocks, envBlocks, prevToken.getLine(), OutlineNode.TYPE_PART, t.getText());
					} else if (prevToken instanceof TCchapter) {
						addSectionNode(blocks, envBlocks, prevToken.getLine(), OutlineNode.TYPE_CHAPTER, t.getText());
					} else if (prevToken instanceof TCsection) {
						addSectionNode(blocks, envBlocks, prevToken.getLine(), OutlineNode.TYPE_SECTION, t.getText());
					} else if (prevToken instanceof TCssection) {
						boolean foundSection = addSectionNode(blocks, envBlocks, prevToken.getLine(),
								OutlineNode.TYPE_SUBSECTION, t.getText()) >= OutlineNode.TYPE_SECTION;

						if (!foundSection && checkForMissingSections) {
							errors.add(new ParseErrorMessage(prevToken.getLine(), prevToken.getPos(),
									prevToken.getText().length() + accumulatedLength + t.getText().length(),
									"Subsection " + prevToken.getText() + " has no preceding section",
									IMarker.SEVERITY_WARNING));
						}
					} else if (prevToken instanceof TCsssection) {
						boolean foundSsection = addSectionNode(blocks, envBlocks, prevToken.getLine(),
								OutlineNode.TYPE_SUBSUBSECTION, t.getText()) >= OutlineNode.TYPE_SUBSECTION;

						if (!foundSsection && checkForMissingSections) {
							errors.add(new ParseErrorMessage(prevToken.getLine(), prevToken.getPos(),
									prevToken.getText().length() + accumulatedLength + t.getText().length(),
									"Subsubsection " + prevToken.getText() + " has no preceding subsection",
									IMarker.SEVERITY_WARNING));
						}
					} else if (prevToken instanceof TCparagraph) {
						boolean foundSssection = addSectionNode(blocks, envBlocks, prevToken.getLine(),
								OutlineNode.TYPE_PARAGRAPH, t.getText()) >= OutlineNode.TYPE_SUBSUBSECTION;

						if (!foundSssection && checkForMissingSections) {
							errors.add(new ParseErrorMessage(prevToken.getLine(), prevToken.getPos(),
									prevToken.getText().length() + accumulatedLength + t.getText().length(),
									"Paragraph " + prevToken.getText() + " has no preceding subsubsection",
									IMarker.SEVERITY_WARNING));
						}
					} else if (prevToken instanceof TCbib) {
						if (biblatexMode) {
							bibs.add(t.getText().trim());
						} else {
							String[] sBibs = t.getText().split(",");
							for (String bib : sBibs) {
								bibs.add(bib.trim());
							}
							int startLine = prevToken.getLine();
							while (!blocks.empty()) {
								OutlineNode prev = blocks.pop();
								if (prev.getType() == OutlineNode.TYPE_ENVIRONMENT) { // this
																						// is
																						// an
																						// error...
									blocks.push(prev);
									break;
								}
								prev.setEndLine(startLine);
							}
						}
					} else if (prevToken instanceof TCbibstyle) {
						this.bibstyle = t.getText();
						int startLine = prevToken.getLine();
						while (!blocks.empty()) {
							OutlineNode prev = blocks.pop();
							if (prev.getType() == OutlineNode.TYPE_ENVIRONMENT) { // this
																					// is
																					// an
																					// error...
								blocks.push(prev);
								break;
							}
							prev.setEndLine(startLine);
						}
					} else if (prevToken instanceof TCinput || prevToken instanceof TCinclude) {
						// inputs.add(t.getText());
						if (!blocks.empty()) {
							OutlineNode prev = blocks.peek();
							OutlineNode on = new OutlineNode(t.getText(), OutlineNode.TYPE_INPUT, t.getLine(), prev);
							on.setEndLine(t.getLine());
							prev.addChild(on);
							inputs.add(on);
						} else {
							OutlineNode on = new OutlineNode(t.getText(), OutlineNode.TYPE_INPUT, t.getLine(), null);
							on.setEndLine(t.getLine());
							outlineTree.add(on);
							inputs.add(on);
						}

					} else if (prevToken instanceof TCnew) {
						// currentCommand = new
						// CommandEntry(t.getText().substring(1));
						currentCommand = new TexCommandEntry(t.getText().substring(1), "", 0);
						currentCommand.startLine = t.getLine();
						lexer.registerCommand(currentCommand.key);
						addCustomCommands(prevToken, currentCommand, t);					
						expectArg2 = true;
					} else if (prevToken instanceof TCpackage) {
						if (t.getText().equals("biblatex")) {
							biblatexMode = true;
							if (packageOptions != null) {
								biblatexBackend = findBiblatexBackend(packageOptions);
								// reset
								packageOptions = null;
							}
						}
						// font style implementation
						if (PDFAccessibility.isPDFAccessibilityModeOn()) {
							
							
							

							if (fontsBlockedPackages.contains(t.getText())) {

								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_FONT,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault().speak(TTSProperties.SEND_DATA_NEXT);
								try {
									Thread.sleep(500);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								TTSConversion.getDefault().speak(
										TTSProperties.MSG_WARNING_ACESBLTY_FONT);
								// PDFAccessibility.setPDFFullyAccessible(false);
								IsWarning = true;

							} 
							else if (trackChangesBlockedPackages.contains(t.getText())) {
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_TRCK_CHNGS,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault()
										.speak(TTSProperties.MSG_WARNING_ACESBLTY_TRCK_CHNGS);
								// PDFAccessibility.setPDFFullyAccessible(false);
								IsWarning = true;
							}
							else if (waterMarkBlockedPackages.contains(t.getText())) {
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_WTRMRKS,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault().speak(
										TTSProperties.MSG_WARNING_ACESBLTY_WTRMRKS);
								IsWarning = true;
								// PDFAccessibility.setPDFFullyAccessible(false);
							}
							else if (AccesibilitPackage.contains(t.getText())) {
							//	errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
								//			TTSProperties.MSG_WARNING_ACESSIBILITY_Package,
								//			IMarker.SEVERITY_WARNING));
								//TTSConversion.getDefault().speak(
								//	TTSProperties.MSG_WARNING_ACESSIBILITY_Package);
								//IsWarning = true;
								IsAccessibilityPackage=true;
								IsWarning=true;
							}
							else if (AccesibilitPackage.contains(t.getText())) {
								errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
										TTSProperties.MSG_WARNING_ACESBLTY_WTRMRKS,
										IMarker.SEVERITY_WARNING));
								TTSConversion.getDefault().speak(
										TTSProperties.MSG_WARNING_ACESBLTY_WTRMRKS);
								IsWarning = true;
								// PDFAccessibility.setPDFFullyAccessible(false);
							}

							
//							else if(commentBlockedPackages.contains(t.getText())){
//								 errors.add(new ParseErrorMessage(t.getLine(),
//								 t.getPos(),
//								 t.getText().length(),
//								 TTSProperties.MSG_WARNING_ACESBLTY_COMENTS,
//								 IMarker.SEVERITY_WARNING));
//								 TTSConversion.getDefault().speak(
//									TTSProperties.MSG_WARNING_ACESBLTY_COMENTS);
//								 IsWarning = true;
//								 // PDFAccessibility.setPDFFullyAccessible(false);
//							}
						}
					}

					// reset state to normal scanning
					accumulatedLength = 0;
					prevToken = null;
					expectArg = false;

				} else if ((t instanceof TCword) && (prevToken instanceof TCnew)) {
					// this handles the \newcommand\comx{...} -format
					// currentCommand = new
					// CommandEntry(t.getText().substring(1));
					currentCommand = new TexCommandEntry(t.getText().substring(1), "", 0);
					currentCommand.startLine = t.getLine();
					lexer.registerCommand(currentCommand.key);

					addCustomCommands(prevToken, currentCommand, t);

					expectArg2 = true;
					accumulatedLength = 0;
					prevToken = null;
					expectArg = false;

				} else if (t instanceof TOptargument) {
					if (prevToken instanceof TCpackage) {
						packageOptions = t.getText();
					}
					accumulatedLength += t.getText().length();
				} else if (!(t instanceof TWhitespace) && !(t instanceof TStar) && !(t instanceof TCommentline)
						&& !(t instanceof TTaskcomment)) {

					// if we didn't get the mandatory argument we were
					// expecting...
					// fatalErrors = true;
					errors.add(new ParseErrorMessage(prevToken.getLine(), prevToken.getPos(),
							prevToken.getText().length() + accumulatedLength + t.getText().length(),
							"No argument following " + prevToken.getText(), IMarker.SEVERITY_WARNING));

					accumulatedLength = 0;
					prevToken = null;
					expectArg = false;
				} else {
					accumulatedLength += t.getText().length();
				}
			} else if (expectArg2) {
				// we are capturing the second argument of a command with two
				// arguments
				// the only one of those that interests us is newcommand
				if (t instanceof TArgument) {
					currentCommand.info = t.getText();
					commands.add(currentCommand);
					if (PART_RE.matcher(currentCommand.info).find())
						sectioning.put("\\" + currentCommand.key, OutlineNode.TYPE_PART);
					// else if (currentCommand.info.indexOf("\\chapter") != -1)
					else if (CHAPTER_RE.matcher(currentCommand.info).find())
						sectioning.put("\\" + currentCommand.key, OutlineNode.TYPE_CHAPTER);
					// else if (currentCommand.info.indexOf("\\section") != -1)
					else if (SECTION_RE.matcher(currentCommand.info).find())
						sectioning.put("\\" + currentCommand.key, OutlineNode.TYPE_SECTION);
					// else if (currentCommand.info.indexOf("\\subsection") !=
					// -1)
					else if (SSECTION_RE.matcher(currentCommand.info).find())
						sectioning.put("\\" + currentCommand.key, OutlineNode.TYPE_SUBSECTION);
					// else if (currentCommand.info.indexOf("\\subsubsection")
					// != -1)
					else if (SSSECTION_RE.matcher(currentCommand.info).find())
						sectioning.put("\\" + currentCommand.key, OutlineNode.TYPE_SUBSUBSECTION);
					// else if (currentCommand.info.indexOf("\\paragraph") !=
					// -1)
					else if (PARAGRAPH_RE.matcher(currentCommand.info).find())
						sectioning.put("\\" + currentCommand.key, OutlineNode.TYPE_PARAGRAPH);
					// else if (currentCommand.info.indexOf("\\label") != -1)
					else if (LABEL_RE.matcher(currentCommand.info).find())
						sectioning.put("\\" + currentCommand.key, LatexParser.TYPE_LABEL);

					argCount = 0;
					expectArg2 = false;
				} else if (t instanceof TOptargument) {
					if (argCount == 0) {
						try {
							currentCommand.arguments = Integer.parseInt(t.getText());
						} catch (NumberFormatException nfe) {
							errors.add(new ParseErrorMessage(prevToken.getLine(), t.getPos(), t.getText().length(),
									"The first optional argument of newcommand must only contain the number of arguments",
									IMarker.SEVERITY_ERROR));
							expectArg2 = false;
						}
					}
					argCount++;
				} else if (!(t instanceof TWhitespace) && !(t instanceof TCommentline)
						&& !(t instanceof TTaskcomment)) {
					// if we didn't get the mandatory argument we were
					// expecting...
					errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
							"No 2nd argument following newcommand", IMarker.SEVERITY_WARNING));
					argCount = 0;
					expectArg2 = false;
				}
			} else {
				if (t instanceof TClabel || t instanceof TCref || t instanceof TCcite || t instanceof TCbib
						|| t instanceof TCbibstyle || t instanceof TCbegin || t instanceof TCend || t instanceof TCinput
						|| t instanceof TCinclude || t instanceof TCpart || t instanceof TCchapter
						|| t instanceof TCsection || t instanceof TCssection || t instanceof TCsssection
						|| t instanceof TCparagraph || t instanceof TCpackage || t instanceof TCnew) {
					prevToken = t;
					expectArg = true;
				} else if (t instanceof TCword) {
					// macros (\newcommand) show up as TCword when used, so we
					// need
					// to check (for each word!) whether it happens to be a
					// command
					if (sectioning.containsKey(t.getText())) {
						nodeType = sectioning.get(t.getText());
						switch (nodeType) {
						case OutlineNode.TYPE_PART:
							prevToken = new TCpart(t.getLine(), t.getPos());
							break;
						case OutlineNode.TYPE_CHAPTER:
							prevToken = new TCchapter(t.getLine(), t.getPos());
							break;
						case OutlineNode.TYPE_SECTION:
							prevToken = new TCsection(t.getLine(), t.getPos());
							break;
						case OutlineNode.TYPE_SUBSECTION:
							prevToken = new TCssection(t.getLine(), t.getPos());
							break;
						case OutlineNode.TYPE_SUBSUBSECTION:
							prevToken = new TCsssection(t.getLine(), t.getPos());
							break;
						case OutlineNode.TYPE_PARAGRAPH:
							prevToken = new TCparagraph(t.getLine(), t.getPos());
							break;
						case LatexParser.TYPE_LABEL:
							prevToken = new TClabel(t.getLine(), t.getPos());
							break;
						default:
							break;
						}
						expectArg = true;
					}

					// if("\\author".equals(t.getText())){
					// authorFound = true;
					// }
					// if("\\title".equals(t.getText())){
					// titleFound = true;
					// }
					// if("\\maketitle".equals(t.getText())){
					// makeTitleFound = true;
					// }
					if (PDFAccessibility.isPDFAccessibilityModeOn()) {
						if ("\\caption".equals(t.getText())) {
							captionFound = true;
						}
						else if  ("\\alt".equals(t.getText())) {
							altFound = true;
						}
					}

				} else if (t instanceof TCpindex) {
					this.index = true;
				} else if (t instanceof TCpbib) {
					int startLine = t.getLine();
					while (!blocks.empty()) {
						OutlineNode prev = blocks.pop();
						if (prev.getType() == OutlineNode.TYPE_ENVIRONMENT) { // this
																				// is
																				// an
																				// error...
							blocks.push(prev);
							break;
						}
						prev.setEndLine(startLine);
					}
					this.localBib = true;
				} else if (t instanceof TTaskcomment) {
					int severity = IMarker.PRIORITY_HIGH;
					int start = t.getText().indexOf("FIXME");
					if (start == -1) {
						severity = IMarker.PRIORITY_NORMAL;
						start = t.getText().indexOf("TODO");
						if (start == -1) {
							start = t.getText().indexOf("XXX");
						}
					}
					String taskText = t.getText().substring(start).trim();
					tasks.add(new ParseErrorMessage(t.getLine(), t.getPos(), taskText.length(), taskText, severity));
				} else if (t instanceof TVtext) {
					// Fold
					OutlineNode on = new OutlineNode(t.getText(), OutlineNode.TYPE_ENVIRONMENT, t.getLine(), t.getPos(),
							t.getText().length());

					// TODO uses memory, but doesn't require much code...
					String[] lines = t.getText().split("\\r\\n|\\n|\\r");
					on.setEndLine(t.getLine() + lines.length);

					if (!blocks.empty()) {
						OutlineNode prev = blocks.peek();
						prev.addChild(on);
						on.setParent(prev);
					} else {
						outlineTree.add(on);
					}
				}
			}
			if (t instanceof TLBrace) {
				braces.push(t);
			} else if (t instanceof TRBrace) {
				if (braces.empty()) {
					// There is an opening brace missing
					errors.add(new ParseErrorMessage(t.getLine(), t.getPos() - 1, 1,
							TexlipsePlugin.getResourceString("parseErrorMissingLBrace"), IMarker.SEVERITY_ERROR));
				} else {
					braces.pop();
				}
			}
			
			if(t.getText().contains("\\renewcommand")){
				fatalErrors = true;
				errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
						TTSProperties.MSG_ERROR_CUSTMCMNDS, IMarker.SEVERITY_ERROR));
			
			}
		}
		// if(!authorFound || !makeTitleFound) {
		// errors.add(new ParseErrorMessage(1,
		// 0,1,
		// "Author name should be provided as per accessibility standards.",
		// IMarker.SEVERITY_WARNING));
		// }
		// if(!titleFound || !makeTitleFound) {
		// errors.add(new ParseErrorMessage(1,
		// 0,1,
		// "Document title should be provided as per accessibility standards.",
		// IMarker.SEVERITY_WARNING));
		// }
		// errors.addAll(AuxFileParser.errors);
		// Check for missing closing braces
		while (!braces.empty()) {
			Token mt = (Token) braces.pop();
			errors.add(new ParseErrorMessage(mt.getLine(), mt.getPos() - 1, 1,
					TexlipsePlugin.getResourceString("parseErrorMissingRBrace"), IMarker.SEVERITY_ERROR));
		}

		int endLine = t.getLine() + 1; // endline is exclusive
		while (!blocks.empty()) {
			OutlineNode prev = blocks.pop();
			prev.setEndLine(endLine);
			if (prev.getType() == OutlineNode.TYPE_ENVIRONMENT) {
				envBlocks.pop();
			}
		}
		while (!envBlocks.empty()) {
			OutlineNode prev = envBlocks.pop();
			prev.setEndLine(endLine);
			fatalErrors = true;
			errors.add(new ParseErrorMessage(prev.getBeginLine(), 0, prev.getName().length(),
					"\\begin{" + prev.getName() + "} does not have matching end; at least one unbalanced begin-end",
					IMarker.SEVERITY_ERROR));
		}
		
		populateErrorStatic(); //populate dummy static error list
		
		if(PDFAccessibility.isPDFAccessibilityModeOn())
		{
			if(IsAccessibilityPackage==false)
			{
				errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
						TTSProperties.MSG_WARNING_ACESSIBILITY_Package,
						IMarker.SEVERITY_WARNING));
				TTSConversion.getDefault().speak(
						TTSProperties.MSG_WARNING_ACESSIBILITY_Package);
			}		
		}
		IsAccessibilityPackage=false;
	}

	private void addCustomCommands(Token prevToken, TexCommandEntry currentCommand, Token t) {
		if (prevToken.getText().equals("\\newcommand"))
			if (!definedCommandsList.contains(currentCommand.key))
				definedCommandsList.add(currentCommand.key);
		if (!PDFAccessibility.isPDFAccessibilityModeOn()) {
			fatalErrors = true;
			errors.add(new ParseErrorMessage(t.getLine(), t.getPos(), t.getText().length(),
					TTSProperties.MSG_ERROR_CUSTMCMNDS, IMarker.SEVERITY_ERROR));
		}
	}
	
	private void addFontsBlockedPackagesToSet(Set<String> fonts) {
		fonts.add("Lmodern");
		fonts.add("bookman");
		fonts.add("tgtermes");
		fonts.add("Tgadventor");
		fonts.add("Tgheros");
		fonts.add("tgschola");
		fonts.add("Palatino");
		fonts.add("Courier");
		fonts.add("mathpazo");
		fonts.add("helvet");
		fonts.add("avant");
		fonts.add("chancery");
		fonts.add("newcent");
		fonts.add("charter");
		fonts.add("fourier");
		fonts.add("eulervm");
		fonts.add("Tgpagella");
		fonts.add("tgbonum");
	}

	private void addTrackChangesBlockedPackagesToSet(Set<String> trackChangesBlockedPackages) {
		trackChangesBlockedPackages.add("changes");
		trackChangesBlockedPackages.add("trackchanges");
	}
	
	
	private void addWaterMarkBlockedPackagesToSet(Set<String> waterMarkBlockedPackages) {
		waterMarkBlockedPackages.add("xwatermark");
		waterMarkBlockedPackages.add("draftwatermark");
	}
	private void addAccessibilityPackagesToSet(Set<String> AccesibilitPackage) {
		AccesibilitPackage.add("accessibility");
	}

	
	private void addCommentBlockedPackagesToSet(Set<String> commentBlockedPackages) {
		commentBlockedPackages.add("pdfcomment");
	}

	/**
	 * @return The labels defined in this document
	 */
	public List<ReferenceEntry> getLabels() {
		return this.labels;
	}

	/**
	 * @return The BibTeX citations
	 */
	public List<DocumentReference> getCites() {
		return this.cites;
	}

	/**
	 * @return The refencing commands
	 */
	public List<DocumentReference> getRefs() {
		return this.refs;
	}

	/**
	 * @return The bibliography files to use.
	 */
	public String[] getBibs() {
		return this.bibs.toArray(new String[0]);
	}

	/**
	 * @return The bibliography style.
	 */
	public String getBibstyle() {
		return bibstyle;
	}

	/**
	 * @return The input commands in this document
	 */
	public List<OutlineNode> getInputs() {
		return this.inputs;
	}

	/**
	 * @return The outline tree of the document (OutlineNode objects).
	 */
	public ArrayList<OutlineNode> getOutlineTree() {
		return this.outlineTree;
	}

	/**
	 * @return The list of errors (ParseErrorMessage objects) in the document
	 */
	public List<ParseErrorMessage> getErrors() {
		return this.errors;
	}
	
	
	/**
	 * @return Whether biblatex mode is activated
	 */
	public boolean isBiblatexMode() {
		return biblatexMode;
	}

	/**
	 * @return The selected biblatex backend
	 */
	public String getBiblatexBackend() {
		return biblatexBackend;
	}

	/**
	 * @return Whether the parsed file contains a bibliography print command.
	 *         This is only relevant if biblatex mode is enabled.
	 */
	public boolean isLocalBib() {
		return localBib;
	}

	/**
	 * @return Returns whether makeindex is to be used or not
	 */
	public boolean isIndex() {
		return index;
	}

	/**
	 * @return Returns the documentEnv.
	 */
	public OutlineNode getDocumentEnv() {
		return documentEnv;
	}

	/**
	 * @return Returns whether there are fatal errors in the document
	 */
	public boolean isFatalErrors() {
		return fatalErrors;
	}

	/**
	 * @return Returns the commands.
	 */
	public ArrayList<TexCommandEntry> getCommands() {
		return commands;
	}

	/**
	 * @return Returns the tasks.
	 */
	public List<ParseErrorMessage> getTasks() {
		return tasks;
	}
	
	public void populateErrorStatic() {
		errorsStatic = new ArrayList<ParseErrorMessage>();
		for (ParseErrorMessage item : errors) {
			  errorsStatic.add(item);
			}
	}

	public static List<ParseErrorMessage> getErrorsStatic() {
		return errorsStatic;
	}
}
