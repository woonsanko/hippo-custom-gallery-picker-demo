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
package org.example.customgallerypicker.demo.repository.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang.StringUtils;
import org.hippoecm.repository.util.RepoUtils;
import org.onehippo.cms7.event.HippoEvent;
import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.cms7.services.eventbus.HippoEventBus;
import org.onehippo.cms7.services.eventbus.Subscribe;
import org.onehippo.repository.events.HippoWorkflowEvent;
import org.onehippo.repository.modules.DaemonModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DaemonModule} implementation which registers a {@link HippoEvent} listener
 * in its {@link #initialize(Session)} method, in order to synchronize the binary folder name
 * whenever user renames a document.
 * <p>
 * For example, if a document at "/content/documents/myhippoproject/announcement/getting-started-with-hippo"
 * is renamed to "/content/documents/myhippoproject/announcement/getting-started-with-hippo-2",
 * then the image upload base path should be also renamed
 * from "/content/gallery/myhippoproject/announcement/getting-started-with-hippo/"
 * to "/content/gallery/myhippoproject/announcement/getting-started-with-hippo-2/" accordingly.
 * </p>
 */
public class BinaryPathUpdaterModule implements DaemonModule {

    private static Logger log = LoggerFactory.getLogger(BinaryPathUpdaterModule.class);

    /**
     * System JCR Session which is given by the Hippo Repository Engine on initialization.
     */
    private Session session;

    /**
     * Hippo Document Renaming Event Listener instance.
     */
    private HippoDocumentRenameEventListener documentRenameEventListener;

    /**
     * {@inheritDoc}
     * <p>
     * This method stores the given {@code session} to use it when making changes on repository later
     * and registers the document renaming event listener to {@link HippoEventBus}.
     * </p>
     */
    @Override
    public void initialize(Session session) throws RepositoryException {
        this.session = session;

        documentRenameEventListener = new HippoDocumentRenameEventListener();
        HippoServiceRegistry.registerService(documentRenameEventListener, HippoEventBus.class);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method unregisters the document renaming event listener from {@link HippoEventBus}.
     * </p>
     */
    @Override
    public void shutdown() {
        if (documentRenameEventListener != null) {
            HippoServiceRegistry.unregisterService(documentRenameEventListener, HippoEventBus.class);
        }
    }

    /**
     * The main Hippo Document Renaming Event listener handler method which is invoked by {@link #documentRenameEventListener}.
     * <p>
     * Basically this method checks if the current final binary folder node name is different from the context document handle node name.
     * If the relative path of the parent node of the final binary folder node is the same as
     * the relative path of the parent node of context document handle node,
     * and if only the final binary folder name is different from the context document handle node name,
     * then this method renames to final binary folder node name to the context document handle node name.
     * </p>
     * @param documentHandleUuid the subject document handle identifier in the context
     */
    protected void handleDocumentRenameHippoEvent(final String documentHandleUuid) {
        try {
            final Node documentHandleNode = session.getNodeByIdentifier(documentHandleUuid);
            final String documentHandleRelPath = StringUtils.removeStart(documentHandleNode.getPath(), "/content/documents/");
            final String documentHandleParentRelPath = StringUtils.removeStart(documentHandleNode.getParent().getPath(), "/content/documents/");

            final Collection<Node> binaryFolderNodes = getLinkedBinaryFolderNodes(documentHandleNode);
            String binaryFolderRelPath;
            String binaryFolderParentRelPath;
            String newBinaryFolderNodePath;
            Node newBinaryFolderNode;

            Node documentHandleTranslationNode;
            Node binaryFolderTranslationNode;
            String translationLanguage;
            String translationMessage;

            boolean anyMoved = false;

            for (Node binaryFolderNode : binaryFolderNodes) {
                binaryFolderRelPath = StringUtils.removeStart(binaryFolderNode.getPath(), "/content/gallery/");
                binaryFolderParentRelPath = StringUtils.removeStart(binaryFolderNode.getParent().getPath(), "/content/gallery/");

                if (StringUtils.equals(binaryFolderParentRelPath, documentHandleParentRelPath)) {
                    if (!StringUtils.equals(binaryFolderRelPath, documentHandleRelPath)) {
                        // Now rename the binary folder name according to the document handle node name here...
                        newBinaryFolderNodePath = binaryFolderNode.getParent().getPath() + "/" + documentHandleNode.getName();
                        session.move(binaryFolderNode.getPath(), newBinaryFolderNodePath);

                        newBinaryFolderNode = session.getNode(newBinaryFolderNodePath);

                        // make sure to have proper mixins again.
                        if (!newBinaryFolderNode.isNodeType("mix:referenceable")) {
                            newBinaryFolderNode.addMixin("mix:referenceable");
                        }
                        if (!newBinaryFolderNode.isNodeType("hippo:translated")) {
                            newBinaryFolderNode.addMixin("hippo:translated");
                        }

                        // let's remove all the hippo:translation child nodes and copy those again from the document handle.
                        for (NodeIterator nodeIt = newBinaryFolderNode.getNodes("hippo:translation"); nodeIt.hasNext(); ) {
                            nodeIt.nextNode().remove();
                        }
                        for (NodeIterator nodeIt = documentHandleNode.getNodes("hippo:translation"); nodeIt.hasNext(); ) {
                            documentHandleTranslationNode = nodeIt.nextNode();
                            translationLanguage = documentHandleTranslationNode.getProperty("hippo:language").getString();
                            translationMessage = documentHandleTranslationNode.getProperty("hippo:message").getString();

                            binaryFolderTranslationNode = newBinaryFolderNode.addNode("hippo:translation", "hippo:translation");
                            binaryFolderTranslationNode.setProperty("hippo:language", translationLanguage);
                            binaryFolderTranslationNode.setProperty("hippo:message", translationMessage);
                        }

                        anyMoved = true;
                    }
                }
            }

            if (anyMoved) {
                session.save();
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while handling document rename workflow event.", e);
        } finally {
            try {
                session.refresh(false);
            } catch (RepositoryException re) {
                log.error("Failed to refresh the session.", re);
            }
        }
    }

    /**
     * Finds all the mirror link compound nodes (e.g, image link nodes),
     * each of which is pointing to a node under /content/gallery/,
     * under the document handle node.
     * @param documentHandleNode the document handle node
     * @return all the mirror link compound nodes (e.g, image link nodes) under the document handle node
     */
    private Collection<Node> getLinkedBinaryFolderNodes(final Node documentHandleNode) {
        Map<String, Node> binaryFolderNodesMap = new HashMap<String, Node>();

        try {
            final String statement = "/jcr:root"
                               + documentHandleNode.getPath()
                               + "//element(*,hippo:facetselect)[@hippo:docbase and @hippo:docbase != 'cafebabe-cafe-babe-cafe-babecafebabe']";
            Query query = session.getWorkspace().getQueryManager().createQuery(RepoUtils.encodeXpath(statement), Query.XPATH);
            QueryResult result = query.execute();
            Node linkNode;
            String docbaseUuid;
            Node binaryHandleNode;
            Node binaryHandleParentNode;

            for (NodeIterator nodeIt = result.getNodes(); nodeIt.hasNext(); ) {
                linkNode = nodeIt.nextNode();

                if (linkNode != null) {
                    docbaseUuid = linkNode.getProperty("hippo:docbase").getString();

                    try {
                        binaryHandleNode = session.getNodeByIdentifier(docbaseUuid);

                        if (!binaryHandleNode.isNodeType("hippo:handle")) {
                            log.error("The binary handle node by docbase, '{}', is not a hippo:handle.", docbaseUuid);
                        } else if (StringUtils.startsWith(binaryHandleNode.getPath(), "/content/gallery/")) {
                            binaryHandleParentNode = binaryHandleNode.getParent();
                            binaryFolderNodesMap.put(binaryHandleParentNode.getPath(), binaryHandleParentNode);
                        }
                    } catch (ItemNotFoundException infe) {
                        log.error("Cannot find the binary handle node by docbase: {}", docbaseUuid);
                    }
                }
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while handling document rename workflow event.", e);
        }

        return binaryFolderNodesMap.values();
    }

    /**
     * {@link HippoEventBus} event listener subscribing {@link HippoEvent}.
     * <p>
     * This handles the event only when the category of the {@link HippoEvent} is 'workflow'
     * and the action of the event is 'replaceAllLocalizedNames'.
     * </p>
     * <p>
     * For your information, when a user renames a document in CMS UI, it may triggers 'rename' action
     * followed by 'replaceAllLocalizedNames' action. Or it may triggers only 'replaceAllLocalizedNames' action.
     * The first case happens if the user changes both the label of the document and the URL of the document.
     * The second case happens if the user changes only the label of the document.
     * </p>
     * <p>
     * Therefore, it's safer to handle 'replaceAllLocalizedNames' action here because it always happens in both cases.
     * </p>
     */
    public class HippoDocumentRenameEventListener {

        @Subscribe
        public void handleEvent(HippoEvent<?> event) {
            if ("workflow".equals(event.category())) {
                // If editor changes only the label of the document, it posts 'replaceAllLocalizedNames' action only.
                // If editor changes both the label and the URL (node) name of the document, it posts 'rename' action followed by 'replaceAllLocalizedNames' action.
                // Therefore, it's safer to handle 'replaceAllLocalizedNames' action here.
                if ("replaceAllLocalizedNames".equals(event.action())) {
                    handleDocumentRenameHippoEvent(((HippoWorkflowEvent) event).subjectId());
                }
            }
        }

    }
}
