/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.sics.ms.ui;

import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import se.sics.ms.types.IndexEntry;
import se.sics.ms.types.SearchPattern;

/**
 *
 * @author jdowling
 */
public class SearchSwingUi extends javax.swing.JDialog {
    private final UiComponent component;
    private static final DefaultListModel model = new DefaultListModel();

    
    /**
     * Creates new form SearchSwingUi
     * @param parent
     * @param modal
     * @param component
     */
    public SearchSwingUi(java.awt.Frame parent, boolean modal,
    final UiComponent component) {
        super(parent, modal);
        initComponents();

        this.component = component;
    }

    public JPanel getRoot() {
        return root;
    }
    
    public void showSearchResults(final IndexEntry[] results) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                model.removeAllElements();
                for (IndexEntry entry : results)
                    model.addElement(indexEntryToString(entry));
                root.updateUI();
            }
        });
    }
    
    private String indexEntryToString(IndexEntry entry) {
        StringBuilder sb = new StringBuilder(entry.getFileName());
        sb.append(", ");
        sb.append(entry.getCategory());
        sb.append(", ");
        sb.append(entry.getLanguage());
        sb.append(", ");
        sb.append(entry.getFileSize());
        sb.append(", ");
        sb.append(entry.getUrl());
        sb.append(", ");
        sb.append(entry.getUploaded());

        return sb.toString();
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        root = new javax.swing.JPanel();
        bookCheckBox = new javax.swing.JCheckBox();
        videoCheckBox = new javax.swing.JCheckBox();
        musicCheckBox = new javax.swing.JCheckBox();
        searchField = new javax.swing.JTextField();
        searchButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        searchResultsList = new javax.swing.JList();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        bookCheckBox.setText("book");

        videoCheckBox.setText("video");

        musicCheckBox.setText("music");

        searchButton.setText("search");
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        searchResultsList.setModel(model);
        jScrollPane1.setViewportView(searchResultsList);

        javax.swing.GroupLayout rootLayout = new javax.swing.GroupLayout(root);
        root.setLayout(rootLayout);
        rootLayout.setHorizontalGroup(
            rootLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rootLayout.createSequentialGroup()
                .addGroup(rootLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(rootLayout.createSequentialGroup()
                        .addGap(170, 170, 170)
                        .addComponent(searchField, javax.swing.GroupLayout.PREFERRED_SIZE, 400, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(searchButton))
                    .addGroup(rootLayout.createSequentialGroup()
                        .addGap(263, 263, 263)
                        .addComponent(videoCheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(musicCheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(bookCheckBox))
                    .addGroup(rootLayout.createSequentialGroup()
                        .addGap(22, 22, 22)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 782, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(28, Short.MAX_VALUE))
        );
        rootLayout.setVerticalGroup(
            rootLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rootLayout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(rootLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(searchButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(rootLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(videoCheckBox)
                    .addComponent(musicCheckBox)
                    .addComponent(bookCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 232, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(28, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(root, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(22, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(root, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
               String title = searchField.getText();
                SearchPattern pattern = new SearchPattern(title);
                component.search(pattern);        
    }//GEN-LAST:event_searchButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox bookCheckBox;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JCheckBox musicCheckBox;
    private javax.swing.JPanel root;
    private javax.swing.JButton searchButton;
    private javax.swing.JTextField searchField;
    private javax.swing.JList searchResultsList;
    private javax.swing.JCheckBox videoCheckBox;
    // End of variables declaration//GEN-END:variables
}