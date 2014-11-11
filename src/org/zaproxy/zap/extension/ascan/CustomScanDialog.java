/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Copyright 2013 ZAP development team
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 */
package org.zaproxy.zap.extension.ascan;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListCellRenderer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.Highlight;
import javax.swing.text.Highlighter.HighlightPainter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.FileConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.core.scanner.PluginFactory;
import org.parosproxy.paros.core.scanner.ScannerParam;
import org.parosproxy.paros.core.scanner.VariantUserDefined;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.OptionsParam;
import org.parosproxy.paros.model.Session;
import org.parosproxy.paros.model.SiteNode;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.view.AbstractParamContainerPanel;
import org.zaproxy.zap.extension.script.ScriptWrapper;
import org.zaproxy.zap.extension.users.ExtensionUserManagement;
import org.zaproxy.zap.model.Context;
import org.zaproxy.zap.model.Target;
import org.zaproxy.zap.model.Tech;
import org.zaproxy.zap.model.TechSet;
import org.zaproxy.zap.users.User;
import org.zaproxy.zap.utils.ZapTextArea;
import org.zaproxy.zap.view.JCheckBoxTree;
import org.zaproxy.zap.view.LayoutHelper;
import org.zaproxy.zap.view.StandardFieldsDialog;

public class CustomScanDialog extends StandardFieldsDialog {

    private static final String FIELD_START = "ascan.custom.label.start";
    private static final String FIELD_CONTEXT = "ascan.custom.label.context";
    private static final String FIELD_USER = "ascan.custom.label.user";
    private static final String FIELD_RECURSE = "ascan.custom.label.recurse";
    private static final String FIELD_ADVANCED = "ascan.custom.label.adv"; 

    private static final String FIELD_VARIANT_INJECTABLE = "variant.options.injectable.label";
    private static final String FIELD_VARIANT_URL_QUERY = "variant.options.injectable.querystring.label";
    private static final String FIELD_VARIANT_URL_PATH = "variant.options.injectable.urlpath.label";
    private static final String FIELD_VARIANT_POST_DATA = "variant.options.injectable.postdata.label";
    private static final String FIELD_VARIANT_HEADERS = "variant.options.injectable.headers.label";
    private static final String FIELD_VARIANT_COOKIE = "variant.options.injectable.cookie.label";
    private static final String FIELD_VARIANT_MULTIPART = "variant.options.rpc.multipart.label";
    
    private static final String FIELD_VARIANT_RPC = "variant.options.rpc.label";
    private static final String FIELD_VARIANT_XML = "variant.options.rpc.xml.label";
    private static final String FIELD_VARIANT_JSON = "variant.options.rpc.json.label";
    private static final String FIELD_VARIANT_GWT = "variant.options.rpc.gwt.label";
    private static final String FIELD_VARIANT_ODATA = "variant.options.rpc.odata.label";
    private static final String FIELD_VARIANT_CUSTOM = "variant.options.rpc.custom.label";

    private static final String FIELD_DISABLE_VARIANTS_MSG = "variant.options.disable";

    private static Logger logger = Logger.getLogger(CustomScanDialog.class);

    private static final long serialVersionUID = 1L;

    private JButton[] extraButtons = null;

    private ExtensionActiveScan extension = null;
    
    private ExtensionUserManagement extUserMgmt = (ExtensionUserManagement) Control.getSingleton().getExtensionLoader()
			.getExtension(ExtensionUserManagement.NAME);
    
    private int headerLength = -1;
    // The index of the start of the URL path eg after https://www.example.com:1234/ - no point attacking this
    private int urlPathStart = -1;
    private Target target = null;

    private ScannerParam scannerParam = null;
    private OptionsParam optionsParam = null;
    private PluginFactory pluginFactory = null;

    private JPanel customPanel = null;
    private JPanel techPanel = null;
    private ZapTextArea requestField = null;
    private JButton addCustomButton = null;
    private JButton removeCustomButton = null;
    private JList<Highlight> injectionPointList = null;
    private DefaultListModel<Highlight> injectionPointModel = new DefaultListModel<Highlight>();
    private JLabel customPanelStatus = new JLabel();
    private JCheckBox disableNonCustomVectors = null;
	private JCheckBoxTree techTree = null;
	private HashMap<Tech, DefaultMutableTreeNode> techToNodeMap = new HashMap<>();
	private TreeModel techModel = null;
	private SequencePanel sequencePanel = null;

    public CustomScanDialog(ExtensionActiveScan ext, Frame owner, Dimension dim) {
        super(owner, "ascan.custom.title", dim, new String[]{
            "ascan.custom.tab.scope",
            "ascan.custom.tab.input",
            "ascan.custom.tab.custom",
            "ascan.custom.tab.sequence",
            "ascan.custom.tab.tech",
            "ascan.custom.tab.policy"
        });
        this.setModal(true);
        this.extension = ext;

        // The first time init to the default options set, after that keep own copies
        reset(false);
    }

    public void init(Target target) {
        if (target != null) {
        	// If one isnt specified then leave the previously selected one
        	this.target = target;
        }
        logger.debug("init " + this.target);

        this.removeAllFields();
        this.injectionPointModel.clear();
        this.headerLength = -1;
        this.urlPathStart = -1;

        this.addTargetSelectField(0, FIELD_START, this.target, false, false);
        this.addComboField(0, FIELD_CONTEXT, new String[] {}, "");
        this.addComboField(0, FIELD_USER, new String[] {}, "");
        this.addCheckBoxField(0, FIELD_RECURSE, true);
        // This option is always read from the 'global' options
        this.addCheckBoxField(0, FIELD_ADVANCED, extension.getScannerParam().isShowAdvancedDialog());
        this.addPadding(0);

        // Default to Recurse, so always set the warning
        customPanelStatus.setText(Constant.messages.getString("ascan.custom.status.recurse"));

        this.addFieldListener(FIELD_CONTEXT, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setUsers();
                setTech();
            }
        });
        this.addFieldListener(FIELD_RECURSE, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setFieldStates();
            }
        });
        this.addFieldListener(FIELD_ADVANCED, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setAdvancedTabs(getBoolValue(FIELD_ADVANCED));
            }
        });

        int targets = scannerParam.getTargetParamsInjectable();
        this.addCheckBoxField(1, FIELD_VARIANT_INJECTABLE, false);
        this.addCheckBoxField(1, FIELD_VARIANT_URL_QUERY, (targets & ScannerParam.TARGET_QUERYSTRING) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_URL_PATH, (targets & ScannerParam.TARGET_URLPATH) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_POST_DATA, (targets & ScannerParam.TARGET_POSTDATA) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_HEADERS, (targets & ScannerParam.TARGET_HTTPHEADERS) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_COOKIE, (targets & ScannerParam.TARGET_COOKIE) != 0);
        setInjectableCheckbox();

        int rpcEnabled = scannerParam.getTargetParamsEnabledRPC();
        this.addPadding(1);
        this.addCheckBoxField(1, FIELD_VARIANT_RPC, false);
        this.addCheckBoxField(1, FIELD_VARIANT_MULTIPART, (rpcEnabled & ScannerParam.RPC_MULTIPART) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_XML, (rpcEnabled & ScannerParam.RPC_XML) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_JSON, (rpcEnabled & ScannerParam.RPC_JSON) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_GWT, (rpcEnabled & ScannerParam.RPC_GWT) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_ODATA, (rpcEnabled & ScannerParam.RPC_ODATA) != 0);
        this.addCheckBoxField(1, FIELD_VARIANT_CUSTOM, (rpcEnabled & ScannerParam.RPC_CUSTOM) != 0);
        this.addPadding(1);
        this.addReadOnlyField(1, FIELD_DISABLE_VARIANTS_MSG, "", true);
        setRpcCheckbox();

        this.addFieldListener(FIELD_VARIANT_INJECTABLE, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Change all of the 'sub' checkboxes as appropriate
				boolean set = getBoolValue(FIELD_VARIANT_INJECTABLE);
		        setFieldValue(FIELD_VARIANT_URL_QUERY, set);
		        setFieldValue(FIELD_VARIANT_URL_PATH, set);
		        setFieldValue(FIELD_VARIANT_POST_DATA, set);
		        setFieldValue(FIELD_VARIANT_HEADERS, set);
		        setFieldValue(FIELD_VARIANT_COOKIE, set);
			}});
        // And if any of the 'sub' checkboxes are changed flow the change up
        this.addFieldListener(FIELD_VARIANT_URL_QUERY, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setInjectableCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_URL_PATH, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setInjectableCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_POST_DATA, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setInjectableCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_HEADERS, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setInjectableCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_COOKIE, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setInjectableCheckbox();
			}});
        
        this.addFieldListener(FIELD_VARIANT_RPC, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Change all of the 'sub' checkboxes as appropriate
				boolean set = getBoolValue(FIELD_VARIANT_RPC);
		        setFieldValue(FIELD_VARIANT_MULTIPART, set);
		        setFieldValue(FIELD_VARIANT_XML, set);
		        setFieldValue(FIELD_VARIANT_JSON, set);
		        setFieldValue(FIELD_VARIANT_GWT, set);
		        setFieldValue(FIELD_VARIANT_ODATA, set);
		        setFieldValue(FIELD_VARIANT_CUSTOM, set);
			}});
        // And if any of the 'sub' checkboxes are changed flow the change up
        this.addFieldListener(FIELD_VARIANT_MULTIPART, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setRpcCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_XML, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setRpcCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_JSON, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setRpcCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_GWT, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setRpcCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_ODATA, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setRpcCheckbox();
			}});
        this.addFieldListener(FIELD_VARIANT_CUSTOM, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
		        setRpcCheckbox();
			}});

        // Custom vectors panel
        this.setCustomTabPanel(2, getCustomPanel());

        //Sequence panel
  		this.setCustomTabPanel(3, this.getSequencePanel(true));
        
        // Technology panel
        this.setCustomTabPanel(4, getTechPanel());
        
        // Policy panel
        AbstractParamContainerPanel policyPanel
                = new AbstractParamContainerPanel(Constant.messages.getString("ascan.custom.tab.policy"));
        
        String[] ROOT = {};

        PolicyAllCategoryPanel policyAllCategoryPanel
                = new PolicyAllCategoryPanel(optionsParam, scannerParam, this.pluginFactory, null);
        
        policyAllCategoryPanel.setName(Constant.messages.getString("ascan.custom.tab.policy"));

        policyPanel.addParamPanel(null, policyAllCategoryPanel, false);

        for (int i = 0; i < Category.getAllNames().length; i++) {
            policyPanel.addParamPanel(ROOT, Category.getName(i),
                    new PolicyCategoryPanel(i, this.pluginFactory.getAllPlugin()), true);
        }
        
        policyPanel.showDialog(true);

        this.setCustomTabPanel(5, policyPanel);
        
        if (target != null) {
	        // Set up the fields if a node has been specified, otherwise leave as previously set
	        this.populateRequestField(this.target.getStartNode());
	        this.siteNodeSelected(FIELD_START, this.target.getStartNode());	
	        this.setUsers();
	        this.setTech();
        }
        
        if ( ! this.scannerParam.isShowAdvancedDialog()) {
        	// Remove all but the first tab
        	this.setAdvancedTabs(false);
        }
        
        this.pack();
    }
    
    private void setInjectableCheckbox() {
        boolean set = 
        		getBoolValue(FIELD_VARIANT_URL_QUERY) &&
        		getBoolValue(FIELD_VARIANT_URL_PATH) &&
        		getBoolValue(FIELD_VARIANT_POST_DATA) &&
        		getBoolValue(FIELD_VARIANT_HEADERS) &&
        		getBoolValue(FIELD_VARIANT_COOKIE);
       	setFieldValue(FIELD_VARIANT_INJECTABLE, set);
    }

    private void setRpcCheckbox() {
        boolean set = 
        		getBoolValue(FIELD_VARIANT_MULTIPART) &&
        		getBoolValue(FIELD_VARIANT_XML) &&
        		getBoolValue(FIELD_VARIANT_JSON) &&
        		getBoolValue(FIELD_VARIANT_GWT) &&
        		getBoolValue(FIELD_VARIANT_ODATA) &&
        		getBoolValue(FIELD_VARIANT_CUSTOM);
       	setFieldValue(FIELD_VARIANT_RPC, set);
    }

	private void setAdvancedTabs(boolean visible) {
		// Show/hide all except from the first tab
		this.setTabsVisible (new String[] {
	            "ascan.custom.tab.input",
	            "ascan.custom.tab.custom",
	            "ascan.custom.tab.sequence",
	            "ascan.custom.tab.tech",
	            "ascan.custom.tab.policy"
	        }, visible);
	}

	/**
	 * Gets the Sequence panel.
	 * @return The sequence panel
	 */
	private SequencePanel getSequencePanel() {
		return this.getSequencePanel(false);
	}
	
	
	/**
	 * Gets the sequence panel, with a boolean that specifies if it should be be a new instance or the extisting one.
	 * @param reset if set to true, returns a new instance, else the existing instance is returned.
	 * @return
	 */
	private SequencePanel getSequencePanel(boolean reset) {
		if(this.sequencePanel == null || reset) {
			this.sequencePanel = new SequencePanel();
		}
		return this.sequencePanel;
	}

    private void populateRequestField(SiteNode node) {
        try {
            if (node == null || node.getHistoryReference() == null || node.getHistoryReference().getHttpMessage() == null) {
                this.getRequestField().setText("");
                
            } else {
                // Populate the custom vectors http pane
                HttpMessage msg = node.getHistoryReference().getHttpMessage();
                String header = msg.getRequestHeader().toString();
                StringBuilder sb = new StringBuilder();
                sb.append(header);
                this.headerLength = header.length();
                this.urlPathStart = header.indexOf("/", header.indexOf("://") + 2) + 1;	// Ignore <METHOD> http(s)://host:port/
                sb.append(msg.getRequestBody().toString());
                this.getRequestField().setText(sb.toString());

                // Only set the recurse option if the node has children, and disable it otherwise
                JCheckBox recurseChk = (JCheckBox) this.getField(FIELD_RECURSE);
                recurseChk.setEnabled(node.getChildCount() > 0);
                recurseChk.setSelected(node.getChildCount() > 0);
            }
            
            this.setFieldStates();
            
        } catch (Exception e) {
            // 
            this.getRequestField().setText("");
        }

    }

    @Override
    public void targetSelected(String field, Target node) {
        List<String> ctxNames = new ArrayList<String>();
        if (node != null) {
            // The user has selected a new node
            this.target = node;
            if (node.getStartNode() != null) {
                populateRequestField(node.getStartNode());
                
                Session session = Model.getSingleton().getSession();
                List<Context> contexts = session.getContextsForNode(node.getStartNode());
                for (Context context : contexts) {
                	ctxNames.add(context.getName());
                }
            	
            } else if (node.getContext() != null) {
            	ctxNames.add(node.getContext().getName());
            }
            
            this.setTech();
        }
        this.setComboFields(FIELD_CONTEXT, ctxNames, "");
       	this.getField(FIELD_CONTEXT).setEnabled(ctxNames.size() > 0);
    }
    
    private Context getSelectedContext() {
    	String ctxName = this.getStringValue(FIELD_CONTEXT);
    	if (this.extUserMgmt != null && ! this.isEmptyField(FIELD_CONTEXT)) {
            Session session = Model.getSingleton().getSession();
            return session.getContext(ctxName);
    	}
    	return null;
    }

    private User getSelectedUser() {
    	Context context = this.getSelectedContext();
    	if (context != null) {
        	String userName = this.getStringValue(FIELD_USER);
        	List<User> users = this.extUserMgmt.getContextUserAuthManager(context.getIndex()).getUsers();
        	for (User user : users) {
        		if (userName.equals(user.getName())) {
        			return user;
        		}
            }
    	}
    	return null;
    }

    private void setUsers() {
    	Context context = this.getSelectedContext();
        List<String> userNames = new ArrayList<String>();
    	if (context != null) {
        	List<User> users = this.extUserMgmt.getContextUserAuthManager(context.getIndex()).getUsers();
        	userNames.add("");	// The default should always be 'not specified'
        	for (User user : users) {
        		userNames.add(user.getName());
            }
    	}
        this.setComboFields(FIELD_USER, userNames, "");
       	this.getField(FIELD_USER).setEnabled(userNames.size() > 1);	// Theres always 1..
    }

    private void setTech() {
    	Context context = this.getSelectedContext();

		TechSet ts = new TechSet(Tech.builtInTech);
		Iterator<Tech> iter = ts.getIncludeTech().iterator();

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Technology");
		Tech tech;
		DefaultMutableTreeNode parent;
		DefaultMutableTreeNode node;
		while (iter.hasNext()) {
			tech = iter.next();
			if (tech.getParent() != null) {
				parent = techToNodeMap.get(tech.getParent());
			} else {
				parent = null;
			}
			if (parent == null) {
				parent = root;
			}
			node = new DefaultMutableTreeNode(tech.getName());
			parent.add(node);
			techToNodeMap.put(tech, node);
		}

		techModel = new DefaultTreeModel(root);
		techTree.setModel(techModel);
		techTree.expandAll();
		// Default to everything set
		TreePath rootTp = new TreePath(root);
		techTree.checkSubTree(rootTp, true);
		techTree.setCheckBoxEnabled(rootTp, false);

		if (context != null) {
			TechSet techSet = context.getTechSet();
			Iterator<Entry<Tech, DefaultMutableTreeNode>> iter2 = techToNodeMap.entrySet().iterator();
			while (iter.hasNext()) {
				Entry<Tech, DefaultMutableTreeNode> nodeEntry = iter2.next();
				TreePath tp = this.getTechPath(nodeEntry.getValue());
				if (techSet.includes(nodeEntry.getKey())) {
					this.getTechTree().check(tp, true);
				}
			}
		}

    }

	private TreePath getTechPath(TreeNode node) {
		List<TreeNode> list = new ArrayList<>();

		// Add all nodes to list
		while (node != null) {
			list.add(node);
			node = node.getParent();
		}
		Collections.reverse(list);

		// Convert array of nodes to TreePath
		return new TreePath(list.toArray());
	}

    private ZapTextArea getRequestField() {
        if (requestField == null) {
            requestField = new ZapTextArea();
            requestField.setEditable(false);
            requestField.setLineWrap(true);
            requestField.getCaret().setVisible(true);
        }
        return requestField;
    }

    private JPanel getCustomPanel() {
        if (customPanel == null) {
            customPanel = new JPanel(new GridBagLayout());

            JScrollPane scrollPane = new JScrollPane();
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setViewportView(getRequestField());

            JPanel buttonPanel = new JPanel(new GridBagLayout());

            getRequestField().addCaretListener(new CaretListener() {
                @Override
                public void caretUpdate(CaretEvent event) {
                    setFieldStates();
                }
            });

            buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(0, 0, 1, 0.5));	// Spacer
            buttonPanel.add(getAddCustomButton(), LayoutHelper.getGBC(1, 0, 1, 1, 0.0D, 0.0D,
                    GridBagConstraints.BOTH, GridBagConstraints.NORTHWEST, new Insets(5, 5, 5, 5)));
            
            buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(2, 0, 1, 0.5));	// Spacer

            buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(0, 1, 1, 0.5));	// Spacer
            buttonPanel.add(getRemoveCustomButton(), LayoutHelper.getGBC(1, 1, 1, 1, 0.0D, 0.0D,
                    GridBagConstraints.BOTH, GridBagConstraints.NORTHWEST, new Insets(5, 5, 5, 5)));
            
            buttonPanel.add(new JLabel(""), LayoutHelper.getGBC(2, 1, 1, 0.5));	// Spacer

            JScrollPane scrollPane2 = new JScrollPane(getInjectionPointList());
            scrollPane2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            buttonPanel.add(new JLabel(Constant.messages.getString("ascan.custom.label.vectors")),
                    LayoutHelper.getGBC(0, 2, 3, 0.0D, 0.0D));
            
            buttonPanel.add(scrollPane2, LayoutHelper.getGBC(0, 3, 3, 1.0D, 1.0D));

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, buttonPanel);
            splitPane.setDividerLocation(550);
            customPanel.add(splitPane, LayoutHelper.getGBC(0, 0, 1, 1, 1.0D, 1.0D));
            customPanel.add(customPanelStatus, LayoutHelper.getGBC(0, 1, 1, 1, 1.0D, 0.0D));
            customPanel.add(getDisableNonCustomVectors(), LayoutHelper.getGBC(0, 2, 1, 1, 1.0D, 0.0D));
        }
        
        return customPanel;
    }

    private JButton getAddCustomButton() {
        if (addCustomButton == null) {
            addCustomButton = new JButton(Constant.messages.getString("ascan.custom.button.pt.add"));
            addCustomButton.setEnabled(false);

            addCustomButton.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    // Add the selected injection point
                    int userDefStart = getRequestField().getSelectionStart();
                    if (userDefStart >= 0) {
                        int userDefEnd = getRequestField().getSelectionEnd();
                        Highlighter hl = getRequestField().getHighlighter();
                        HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(Color.RED);
                        try {
                            Highlight hlt = (Highlight) hl.addHighlight(userDefStart, userDefEnd, painter);
                            injectionPointModel.addElement(hlt);
                            // Unselect the text
                            getRequestField().setSelectionStart(userDefEnd);
                            getRequestField().setSelectionEnd(userDefEnd);
                            getRequestField().getCaret().setVisible(true);
                            
                        } catch (BadLocationException e1) {
                            logger.error(e1.getMessage(), e1);
                        }
                    }

                }
            });

        }
        return addCustomButton;
    }

    private JButton getRemoveCustomButton() {
        if (removeCustomButton == null) {
            removeCustomButton = new JButton(Constant.messages.getString("ascan.custom.button.pt.rem"));
            removeCustomButton.setEnabled(false);

            removeCustomButton.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    // Remove any selected injection points
                    int userDefStart = getRequestField().getSelectionStart();
                    if (userDefStart >= 0) {
                        int userDefEnd = getRequestField().getSelectionEnd();
                        Highlighter hltr = getRequestField().getHighlighter();
                        Highlight[] hls = hltr.getHighlights();

                        if (hls != null && hls.length > 0) {
                            for (Highlight hl : hls) {
                                if (selectionIncludesHighlight(userDefStart, userDefEnd, hl)) {
                                    hltr.removeHighlight(hl);
                                    injectionPointModel.removeElement(hl);
                                }
                            }
                        }
                        
                        // Unselect the text
                        getRequestField().setSelectionStart(userDefEnd);
                        getRequestField().setSelectionEnd(userDefEnd);
                        getRequestField().getCaret().setVisible(true);
                    }
                }
            });
        }
        
        return removeCustomButton;
    }
    
    private JCheckBox getDisableNonCustomVectors() {
    	if (disableNonCustomVectors == null) {
    		disableNonCustomVectors = new JCheckBox(Constant.messages.getString("ascan.custom.label.disableiv"));
    		disableNonCustomVectors.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					// Enable/disable all of the input vector options as appropriate
		    		getField(FIELD_VARIANT_URL_QUERY).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_URL_PATH).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_POST_DATA).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_HEADERS).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_COOKIE).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_MULTIPART).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_XML).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_JSON).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_GWT).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_ODATA).setEnabled(!disableNonCustomVectors.isSelected());
		    		getField(FIELD_VARIANT_CUSTOM).setEnabled(!disableNonCustomVectors.isSelected());
		    		
		    		if (disableNonCustomVectors.isSelected()) {
		    			setFieldValue(FIELD_DISABLE_VARIANTS_MSG, 
		    					Constant.messages.getString("ascan.custom.warn.disabled"));
		    		} else {
		    			setFieldValue(FIELD_DISABLE_VARIANTS_MSG, "");
		    		}

				}});
    		
    	}
    	return disableNonCustomVectors;
    }

    private JPanel getTechPanel() {
        if (techPanel == null) {
            techPanel = new JPanel(new GridBagLayout());

            JScrollPane scrollPane = new JScrollPane();
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setViewportView(getTechTree());
			scrollPane.setBorder(javax.swing.BorderFactory
					.createEtchedBorder(javax.swing.border.EtchedBorder.RAISED));

            techPanel.add(scrollPane, LayoutHelper.getGBC(0, 0, 1, 1, 1.0D, 1.0D));
        }
        
        return techPanel;
    }

	private JCheckBoxTree getTechTree() {
		if (techTree == null) {
			techTree = new JCheckBoxTree() {
				private static final long serialVersionUID = 1L;

				@Override
				protected void setExpandedState(TreePath path, boolean state) {
					// Ignore all collapse requests; collapse events will not be fired
					if (state) {
						super.setExpandedState(path, state);
					}
				}
			};
			// Initialise the structure based on all the tech we know about
			TechSet ts = new TechSet(Tech.builtInTech);
			Iterator<Tech> iter = ts.getIncludeTech().iterator();

			DefaultMutableTreeNode root = new DefaultMutableTreeNode("Technology");
			Tech tech;
			DefaultMutableTreeNode parent;
			DefaultMutableTreeNode node;
			while (iter.hasNext()) {
				tech = iter.next();
				if (tech.getParent() != null) {
					parent = techToNodeMap.get(tech.getParent());
				} else {
					parent = null;
				}
				if (parent == null) {
					parent = root;
				}
				node = new DefaultMutableTreeNode(tech.getName());
				parent.add(node);
				techToNodeMap.put(tech, node);
			}

			techModel = new DefaultTreeModel(root);
			techTree.setModel(techModel);
			techTree.expandAll();
			techTree.setCheckBoxEnabled(new TreePath(root), false);
            this.setTech();

		}
		return techTree;
	}

    private void setFieldStates() {
        int userDefStart = getRequestField().getSelectionStart();

        if (getBoolValue(FIELD_RECURSE)) {
            // Dont support custom vectors when recursing
            customPanelStatus.setText(Constant.messages.getString("ascan.custom.status.recurse"));
            getAddCustomButton().setEnabled(false);
            getRemoveCustomButton().setEnabled(false);
            getDisableNonCustomVectors().setEnabled(false);
        
        } else {
            customPanelStatus.setText(Constant.messages.getString("ascan.custom.status.highlight"));
            if (userDefStart >= 0) {
                int userDefEnd = getRequestField().getSelectionEnd();
                if (selectionIncludesHighlight(userDefStart, userDefEnd,
                        getRequestField().getHighlighter().getHighlights())) {
                    getAddCustomButton().setEnabled(false);
                    getRemoveCustomButton().setEnabled(true);
                
                } else if (userDefStart < urlPathStart) {
                    // No point attacking the method, hostname or port 
                    getAddCustomButton().setEnabled(false);
                
                } else if (userDefStart < headerLength && userDefEnd > headerLength) {
                    // The users selection cross the header / body boundry - thats never going to work well
                    getAddCustomButton().setEnabled(false);
                    getRemoveCustomButton().setEnabled(false);
                
                } else {
                    getAddCustomButton().setEnabled(true);
                    getRemoveCustomButton().setEnabled(false);
                }
                
            } else {
                // Nothing selected
                getAddCustomButton().setEnabled(false);
                getRemoveCustomButton().setEnabled(false);
            }
            getDisableNonCustomVectors().setEnabled(true);
        }
        
        getRequestField().getCaret().setVisible(true);
    }
    
	private TechSet getTechSet(){
		TechSet techSet = new TechSet();

		Iterator<Entry<Tech, DefaultMutableTreeNode>> iter = techToNodeMap.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<Tech, DefaultMutableTreeNode> node = iter.next();
			TreePath tp = this.getTechPath(node.getValue());
			Tech tech = node.getKey();
			if (this.getTechTree().isSelectedFully(tp)) {
				techSet.include(tech);
			} else {
				techSet.exclude(tech);
			}
		}
		return techSet;
	}


    private JList<Highlight> getInjectionPointList() {
        if (injectionPointList == null) {
            injectionPointList = new JList<Highlight>(injectionPointModel);
            injectionPointList.setCellRenderer(new ListCellRenderer<Highlight>() {
                @Override
                public Component getListCellRendererComponent(
                        JList<? extends Highlight> list, Highlight hlt,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    String str = "";
                    try {
                        str = getRequestField().getText(hlt.getStartOffset(), hlt.getEndOffset() - hlt.getStartOffset());
                        if (str.length() > 8) {
                            // just show first 8 chrs (arbitrary limit;)
                            str = str.substring(0, 8) + "..";
                        }
                    } catch (BadLocationException e) {
                        // Ignore
                    }
                    
                    return new JLabel("[" + hlt.getStartOffset() + "," + hlt.getEndOffset() + "]: " + str);
                }
            });
        }
        return injectionPointList;
    }

    private boolean selectionIncludesHighlight(int start, int end, Highlight hl) {
        if (hl.getPainter() instanceof DefaultHighlighter.DefaultHighlightPainter) {
            DefaultHighlighter.DefaultHighlightPainter ptr = (DefaultHighlighter.DefaultHighlightPainter) hl.getPainter();
            if (ptr.getColor() != null && ptr.getColor().equals(Color.RED)) {
                // Test for 'RED' needed to prevent matching the users selection
                return start < hl.getEndOffset() && end > hl.getStartOffset();
            }
        }
        return false;
    }

    private boolean selectionIncludesHighlight(int start, int end, Highlight[] hls) {
        for (Highlight hl : hls) {
            if (this.selectionIncludesHighlight(start, end, hl)) {
                return true;
            }
        }
        return false;
    }

    private void reset(boolean refreshUi) {
        FileConfiguration fileConfig = new XMLConfiguration();
        ConfigurationUtils.copy(extension.getScannerParam().getConfig(), fileConfig);

        scannerParam = new ScannerParam();
        scannerParam.load(fileConfig);

        optionsParam = new OptionsParam();
        optionsParam.load(fileConfig);

        pluginFactory = Control.getSingleton().getPluginFactory().clone();

        if (refreshUi) {
            init(target);
            repaint();
        }
    }

    @Override
    public String getSaveButtonText() {
        return Constant.messages.getString("ascan.custom.button.scan");
    }

    @Override
    public JButton[] getExtraButtons() {
        if (extraButtons == null) {
            JButton resetButton = new JButton(Constant.messages.getString("ascan.custom.button.reset"));
            resetButton.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    reset(true);
                }
            });

            extraButtons = new JButton[]{resetButton};
        }
        return extraButtons;
    }

    @Override
    public void save() {
        Object[] contextSpecificObjects = null;
        if (this.getBoolValue(FIELD_ADVANCED)) {
	        // Set Injectable Targets
	        int targets = 0;
	        int enabledRpc = 0;
	
	        if (! getDisableNonCustomVectors().isSelected()) {
	        	// Only set these if the user hasnt disabled them
		        if (this.getBoolValue(FIELD_VARIANT_URL_QUERY)) {
		            targets |= ScannerParam.TARGET_QUERYSTRING;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_URL_PATH)) {
		            targets |= ScannerParam.TARGET_URLPATH;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_POST_DATA)) {
		            targets |= ScannerParam.TARGET_POSTDATA;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_HEADERS)) {
		            targets |= ScannerParam.TARGET_HTTPHEADERS;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_COOKIE)) {
		            targets |= ScannerParam.TARGET_COOKIE;
		        }
	
		        // Set Enabled RPC schemas
		        if (this.getBoolValue(FIELD_VARIANT_MULTIPART)) {
		            enabledRpc |= ScannerParam.RPC_MULTIPART;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_XML)) {
		            enabledRpc |= ScannerParam.RPC_XML;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_JSON)) {
		            enabledRpc |= ScannerParam.RPC_JSON;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_GWT)) {
		            enabledRpc |= ScannerParam.RPC_GWT;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_ODATA)) {
		            enabledRpc |= ScannerParam.RPC_ODATA;
		        }
		        
		        if (this.getBoolValue(FIELD_VARIANT_CUSTOM)) {
		            enabledRpc |= ScannerParam.RPC_CUSTOM;
		        }
	    	}        
	        
	        //The following List and Hashmap represent the selections made on the Sequence Panel.
	  		List<ScriptWrapper> selectedIncludeScripts = getSequencePanel().getSelectedIncludeScripts();
	        
	        if (!getBoolValue(FIELD_RECURSE) && injectionPointModel.getSize() > 0) {
	            int[][] injPoints = new int[injectionPointModel.getSize()][];
	            for (int i = 0; i < injectionPointModel.getSize(); i++) {
	                Highlight hl = injectionPointModel.elementAt(i);
	                injPoints[i] = new int[2];
	                injPoints[i][0] = hl.getStartOffset();
	                injPoints[i][1] = hl.getEndOffset();
	            }
	
	            try {
	            	if (target != null && target.getStartNode() != null) {
		                VariantUserDefined.setInjectionPoints(
		                        this.target.getStartNode().getHistoryReference().getURI().toString(),
		                        injPoints);
		                
		                enabledRpc |= ScannerParam.RPC_USERDEF;
	            	}
	                
	            } catch (Exception e) {
	                logger.error(e.getMessage(), e);
	            }
	        }
	        scannerParam.setTargetParamsInjectable(targets);
	        scannerParam.setTargetParamsEnabledRPC(enabledRpc);
	
	        contextSpecificObjects = new Object[]{
	            scannerParam,
	            pluginFactory.clone(),
	            this.getTechSet()
	        };
	        
	        this.extension.setIncludedSequenceScripts(selectedIncludeScripts);
    	}

        target.setRecurse(this.getBoolValue(FIELD_RECURSE));
        
        // Save the adv option permanently for next time
        extension.getScannerParam().setShowAdvancedDialog(this.getBoolValue(FIELD_ADVANCED));

        this.extension.startScan(
                target,
                getSelectedUser(), 
                contextSpecificObjects);
    }

    @Override
    public String validateFields() {
        if (this.target == null || !this.target.isValid()) {
            return Constant.messages.getString("ascan.custom.nostart.error");
        }
        
        return null;
    }
}
