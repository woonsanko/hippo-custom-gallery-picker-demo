/*
 *  Copyright 2015-2015 Hippo B.V. (http://www.onehippo.com)
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.example.customgallerypicker.demo.cms.plugins;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang.StringUtils;
import org.hippoecm.frontend.model.JcrNodeModel;
import org.hippoecm.frontend.plugin.config.IPluginConfig;
import org.hippoecm.frontend.plugin.config.impl.AbstractPluginDecorator;
import org.hippoecm.frontend.plugins.standards.picker.NodePickerControllerSettings;
import org.hippoecm.frontend.session.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorating {@link IPluginConfig} implementation
 * which is determining the <code>base.uuid</code> configuration value dynamically at runtime
 * based on the current context document handle node path.
 */
public class BinaryPathDeterminingPluginConfig extends AbstractPluginDecorator {

    private static Logger log = LoggerFactory.getLogger(BinaryPathDeterminingPluginConfig.class);

    /**
     * Current context binary link field compound node model.
     */
    private final JcrNodeModel contextFieldNodeModel;

    /**
     * Constructor.
     * @param upstream the original upstream {@link IPluginConfig} instance to decorate
     * @param contextFieldNodeModel current context binary link field compound node model
     */
    public BinaryPathDeterminingPluginConfig(IPluginConfig upstream, final JcrNodeModel contextFieldNodeModel) {
        super(upstream);
        this.contextFieldNodeModel = contextFieldNodeModel;
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the key is 'base.uuid', this should try to determine the binary folder path to upload the binary files
     * based on the current context document handle node path.
     * If the binary folder doesn't exist yet, it should also create the binary folder nodes automatically.
     * </p>
     * <p>
     * Also, it should turn off 'last.visited.enabled' in order to show the context related binary folder path
     * instead of the last visited binary folder.
     * </p>
     */
    @Override
    public Object get(Object key) {
        Object obj = upstream.get(key);

        if (NodePickerControllerSettings.BASE_UUID.equals(key)) {
            obj = getContextBaseUuid();
        } else if (NodePickerControllerSettings.LAST_VISITED_ENABLED.equals(key)) {
            obj = Boolean.FALSE.toString();
        }

        if (obj == null) {
            return null;
        }

        return wrap(obj);
    }

    /**
     * Returns the orignal configuration object without decorating the value itself.
     */
    @Override
    protected Object decorate(Object object) {
        return object;
    }

    /**
     * Determines the context related binary folder node and returns the identifier of the folder node.
     * This method can return null if it cannot determine the context related binary folder node.
     * @return the identifier of the context related binary folder node or null if determination is impossible
     */
    private String getContextBaseUuid() {
        String baseUuid = null;

        try {
            final Node binaryFolderNode = getContextBinaryFolderNode();

            if (binaryFolderNode != null) {
                baseUuid = binaryFolderNode.getIdentifier();
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while reading the current context node path.", e);
        }

        return baseUuid;
    }

    /**
     * Determines the context related binary folder node.
     * This method can return null if it cannot determine the context related binary folder node.
     * @return the context related binary folder node or null if determination is impossible
     */
    private Node getContextBinaryFolderNode() {
        Node binaryFolderNode = null;

        try {
            final Node contextDocumentHandleNode = getContextDocumentHandleNode();

            if (contextDocumentHandleNode != null) {
                binaryFolderNode = createBinaryFolderNodeFor(contextDocumentHandleNode);
                log.debug("binaryFolderNode: {}", binaryFolderNode.getPath());
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while finding the current context binary folder node.", e);
        }

        return binaryFolderNode;
    }

    /**
     * Creates context related binary folder nodes if not existing, and returns the target binary folder node.
     * @param contextDocumentHandleNode the context document handle node
     * @return the target binary folder node.
     * @throws RepositoryException repository exception if failing to create or get the binary folder nodes.
     */
    private Node createBinaryFolderNodeFor(final Node contextDocumentHandleNode) throws RepositoryException {
        String documentHandleRelPath = StringUtils.removeStart(contextDocumentHandleNode.getPath(), "/content/documents/");
        final Session session = UserSession.get().getJcrSession();

        Node binaryFolderNode = session.getRootNode().getNode("content/gallery");
        Node documentFolderNode = session.getRootNode().getNode("content/documents");

        Node documentFolderTranslationNode;
        Node binaryFolderTranslationNode;
        String translationLanguage;
        String translationMessage;

        // Find all the node names in the path.
        String [] nodeNames = StringUtils.split(documentHandleRelPath, "/");

        boolean changed = false;

        try {
            for (String nodeName : nodeNames) {
                documentFolderNode = documentFolderNode.getNode(nodeName);

                if (binaryFolderNode.hasNode(nodeName)) {
                    binaryFolderNode = binaryFolderNode.getNode(nodeName);
                } else {
                    // add a binary folder node (type of 'hippogallery:stdImageGallery')
                    binaryFolderNode = binaryFolderNode.addNode(nodeName, "hippogallery:stdImageGallery");
                    // add needed mixins for binary folder node
                    binaryFolderNode.addMixin("mix:referenceable");
                    binaryFolderNode.addMixin("hippo:translated");
                    // add needed properties for binary folder node
                    binaryFolderNode.setProperty("hippostd:foldertype", new String [] { "new-image-folder" });
                    binaryFolderNode.setProperty("hippostd:gallerytype", new String [] { "hippogallery:imageset" });

                    // let's copy all the hippo:translation nodes under the document handle node
                    // to the binary folder node in order to look nicer for end users.
                    for (NodeIterator nodeIt = documentFolderNode.getNodes("hippo:translation"); nodeIt.hasNext(); ) {
                        documentFolderTranslationNode = nodeIt.nextNode();

                        if (documentFolderTranslationNode != null) {
                            translationLanguage = documentFolderTranslationNode.getProperty("hippo:language").getString();
                            translationMessage = documentFolderTranslationNode.getProperty("hippo:message").getString();

                            binaryFolderTranslationNode = binaryFolderNode.addNode("hippo:translation", "hippo:translation");
                            binaryFolderTranslationNode.setProperty("hippo:language", translationLanguage);
                            binaryFolderTranslationNode.setProperty("hippo:message", translationMessage);
                        }
                    }

                    changed = true;
                }
            }

            if (changed) {
                session.save();
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while finding/creating the current context binary folder node.", e);
            session.refresh(false);
        }

        return binaryFolderNode;
    }

    /**
     * Finds and returns the context document handle node.
     * @return the context document handle node
     */
    private Node getContextDocumentHandleNode() {
        Node handleNode = null;

        try {
            Node curNode = contextFieldNodeModel.getNode();

            while (curNode != null && !curNode.isNodeType("hippostdpubwf:document")) {
                curNode = curNode.getParent();
            }

            if (curNode != null) {
                handleNode = curNode.getParent();
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while finding the current context document handle node.", e);
        }

        return handleNode;
    }

}
