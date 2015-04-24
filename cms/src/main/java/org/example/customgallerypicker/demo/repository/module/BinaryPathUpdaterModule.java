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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.apache.commons.collections.CollectionUtils;
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
 * whenever user renames a document or a folder.
 * <p>
 * For example, if a document at "/content/documents/myhippoproject/announcement/getting-started-with-hippo"
 * is renamed to "/content/documents/myhippoproject/announcement/getting-started-with-hippo-2",
 * then the image upload base folder should be also renamed
 * from "/content/gallery/myhippoproject/announcement/getting-started-with-hippo/"
 * to "/content/gallery/myhippoproject/announcement/getting-started-with-hippo-2/" accordingly.
 * </p>
 * <p>
 * Also, if the folder at "/content/documents/myhippoproject/announcement/"
 * is renamed to "/content/documents/myhippoproject/announcement2/",
 * then the interim image upload base folder should be also renamed
 * from "/content/gallery/myhippoproject/announcement2/getting-started-with-hippo/"
 * to "/content/gallery/myhippoproject/announcement2/getting-started-with-hippo-2/" accordingly.
 * </p>
 */
public class BinaryPathUpdaterModule implements DaemonModule {

    private static Logger log = LoggerFactory.getLogger(BinaryPathUpdaterModule.class);

    /**
     * Default property value of {@code hippostd:foldertype} of {@code hippogallery:stdImageGallery}.
     */
    private static final String [] GALLERY_NODE_FOLDER_TYPES = { "new-image-folder" };

    /**
     * Default property value of {@code hippostd:gallerytype} of {@code hippogallery:stdImageGallery}.
     */
    private static final String [] GALLERY_NODE_GALLERY_TYPES = { "hippogallery:imageset" };

    /**
     * System JCR Session which is given by the Hippo Repository Engine on initialization.
     */
    private Session session;

    /**
     * Hippo Document or Folder Renaming Event Listener instance.
     */
    private HippoDocumentRenameEventListener documentOrFolderRenameEventListener;

    /**
     * {@inheritDoc}
     * <p>
     * This method stores the given {@code session} to use it when making changes on repository later
     * and registers the document or folder renaming event listener to {@link HippoEventBus}.
     * </p>
     */
    @Override
    public void initialize(Session session) throws RepositoryException {
        this.session = session;

        documentOrFolderRenameEventListener = new HippoDocumentRenameEventListener();
        HippoServiceRegistry.registerService(documentOrFolderRenameEventListener, HippoEventBus.class);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method unregisters the document or folder renaming event listener from {@link HippoEventBus}.
     * </p>
     */
    @Override
    public void shutdown() {
        if (documentOrFolderRenameEventListener != null) {
            HippoServiceRegistry.unregisterService(documentOrFolderRenameEventListener, HippoEventBus.class);
        }
    }

    /**
     * Handles folder renaming hippo event.
     * @param folderNode folder node
     * @param subjectPath old folder path (the original folder path before renaming)
     * @param arguments folder workflow arguments containing old folder path and new folder path as ordered.
     */
    private void handleFolderRenameHippoEvent(final Node folderNode, final String subjectPath, final List<String> arguments) {
        try {
            final String oldChildFolderNodeName = CollectionUtils.isEmpty(arguments) ? null : arguments.get(0);
            final String newChildFolderNodeName = CollectionUtils.size(arguments) < 2 ? null : arguments.get(1);
            Node newChildFolderNode = null;

            if (StringUtils.isNotBlank(newChildFolderNodeName) && folderNode.hasNode(newChildFolderNodeName)) {
                newChildFolderNode = folderNode.getNode(newChildFolderNodeName);
            }

            if (newChildFolderNode != null) {
                final Collection<Node> binaryFolderNodes = getLinkedBinaryFolderNodes(newChildFolderNode);

                boolean anyUpdated = false;

                for (Node binaryFolderNode : binaryFolderNodes) {
                    if (synchronizeEachBinaryFolderByFolder(binaryFolderNode, newChildFolderNode, folderNode.getPath() + "/" + oldChildFolderNodeName)) {
                        anyUpdated = true;
                    }
                }

                if (anyUpdated) {
                    session.save();
                }
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while synchronizing binary folders by document handle.", e);
        } finally {
            try {
                session.refresh(false);
            } catch (RepositoryException re) {
                log.error("Failed to refresh the session.", re);
            }
        }
    }

    /**
     * Handles document renaming hippo event.
     * @param documentHandleNode document handle node
     * @param subjectPath document handle node path
     * @param arguments folder workflow arguments containing old folder path and new folder path as ordered.
     */
    private void handleDocumentRenameHippoEvent(final Node documentHandleNode, final String subjectPath, final List<String> arguments) {
        try {
            final Collection<Node> binaryFolderNodes = getLinkedBinaryFolderNodes(documentHandleNode);

            boolean anyUpdated = false;

            for (Node binaryFolderNode : binaryFolderNodes) {
                if (synchronizeEachBinaryFolderByDocumentHandle(binaryFolderNode, documentHandleNode)) {
                    anyUpdated = true;
                }
            }

            if (anyUpdated) {
                session.save();
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while synchronizing binary folders by document handle.", e);
        } finally {
            try {
                session.refresh(false);
            } catch (RepositoryException re) {
                log.error("Failed to refresh the session.", re);
            }
        }
    }

    /**
     * Handles document moving hippo event.
     * @param documentHandleNode document handle node
     * @param subjectPath document handle node path
     * @param arguments folder workflow arguments containing old folder path and new folder path as ordered.
     */
    private void handleDocumentMoveHippoEvent(final Node documentHandleNode, final String subjectPath, final List<String> arguments) {
        try {
            final String oldDocumentHandleRelPath = StringUtils.removeStart(subjectPath, "/content/documents/");
            final String newDocumentHandleRelPath = StringUtils.removeStart(documentHandleNode.getPath(), "/content/documents/");
            final String newDocumentHandleParentRelPath = StringUtils.removeStart(documentHandleNode.getParent().getPath(), "/content/documents/");
            final String sourceBinaryFolderPath = "/content/gallery/" + oldDocumentHandleRelPath;
            final String targetBinaryFolderPath = "/content/gallery/" + newDocumentHandleRelPath;

            if (StringUtils.equals(sourceBinaryFolderPath, targetBinaryFolderPath)) {
                log.warn("Source binary folder path and target binary folder path are the same!");
            } else {
                if (!session.nodeExists(sourceBinaryFolderPath)) {
                    log.debug("Source binary folder doesn't exist.");
                } else {
                    createBinaryFoldersIfNotExisting(newDocumentHandleParentRelPath);
                    session.move(sourceBinaryFolderPath, targetBinaryFolderPath);
                    session.save();
                }
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while synchronizing binary folders by moved document handle.", e);
        } finally {
            try {
                session.refresh(false);
            } catch (RepositoryException re) {
                log.error("Failed to refresh the session.", re);
            }
        }
    }

    /**
     * Create gallery binary folder nodes if not existing.
     * @param relPath
     * @throws RepositoryException
     */
    private void createBinaryFoldersIfNotExisting(final String relPath) throws RepositoryException {
        final String [] folderNames = StringUtils.split(relPath, "/");

        if (folderNames != null) {
            Node galleryNode = session.getNode("/content/gallery");
            Node docFolderNode = session.getNode("/content/documents");
            String folderName;
            boolean updated = false;

            for (int i = 0; i < folderNames.length; i++) {
                folderName = StringUtils.substringBefore(folderNames[i], "[");
                docFolderNode = docFolderNode.getNode(folderName);

                if (galleryNode.hasNode(folderName)) {
                    galleryNode = galleryNode.getNode(folderName);
                } else {
                    galleryNode = galleryNode.addNode(folderName, "hippogallery:stdImageGallery");
                    galleryNode.addMixin("hippo:translated");
                    galleryNode.addMixin("mix:referenceable");
                    galleryNode.setProperty("hippostd:foldertype", GALLERY_NODE_FOLDER_TYPES);
                    galleryNode.setProperty("hippostd:gallerytype", GALLERY_NODE_GALLERY_TYPES);

                    copyTranslationNodes(docFolderNode, galleryNode);

                    updated = true;
                }
            }

            if (updated) {
                session.save();
            }
        }
    }

    /**
     * Synchronize each interim binary folder node name based on the renamed folder node.
     * @param binaryFolderNode the final binary folder node from which the interim binary folder should be calculated
     * @param folderNode the context folder node
     * @param oldFolderPath the original folder node path (before renaming)
     * @return true if any updated
     * @throws RepositoryException repository exception if it fails to move nodes.
     */
    private boolean synchronizeEachBinaryFolderByFolder(Node binaryFolderNode, Node folderNode, String oldFolderPath) throws RepositoryException {
        boolean updated = false;

        try {
            final String oldFolderRelPath = StringUtils.removeStart(oldFolderPath, "/content/documents/");
            final String binaryFolderRelPath = StringUtils.removeStart(binaryFolderNode.getPath(), "/content/gallery/");

            if (StringUtils.startsWith(binaryFolderRelPath, oldFolderRelPath)) {
                Node interimBinaryFolderNode = session.getNode("/content/gallery/" + oldFolderRelPath);

                if (moveBinaryFolderNodeByBaseNode(interimBinaryFolderNode, folderNode)) {
                    updated = true;
                }
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while synchronizing single binary folder by document handle.", e);
        }

        return updated;
    }

    /**
     * Synchronize each binary folder node name based on the renamed document handle node.
     * @param binaryFolderNode the final binary folder node
     * @param documentHandleNode the context document handle node
     * @return true if any updated
     * @throws RepositoryException repository exception if it fails to move nodes.
     */
    private boolean synchronizeEachBinaryFolderByDocumentHandle(Node binaryFolderNode, Node documentHandleNode) throws RepositoryException {
        boolean updated = false;

        try {
            final String documentHandleParentRelPath = StringUtils.removeStart(documentHandleNode.getParent().getPath(), "/content/documents/");
            final String binaryFolderParentRelPath = StringUtils.removeStart(binaryFolderNode.getParent().getPath(), "/content/gallery/");

            if (StringUtils.equals(binaryFolderParentRelPath, documentHandleParentRelPath)) {
                if (moveBinaryFolderNodeByBaseNode(binaryFolderNode, documentHandleNode)) {
                    updated = true;
                }
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while synchronizing single binary folder by document handle.", e);
        }

        return updated;
    }

    /**
     * Rename (move) the {@code binaryFolderNode} based on the corresponding base node
     * (which is either document handle node or interim folder node).
     * @param binaryFolderNode binary folder node
     * @param correspondingBaseNode corresponding base node (which is either document handle node or interim folder node)
     * @throws RepositoryException repository exception if node moving (renaming) fails.
     */
    private boolean moveBinaryFolderNodeByBaseNode(Node binaryFolderNode, Node correspondingBaseNode) throws RepositoryException {
        boolean updated = false;

        // Now rename the binary folder name according to the document handle node name here...
        String oldBinaryFolderNodePath = binaryFolderNode.getPath();
        String newBinaryFolderNodePath = binaryFolderNode.getParent().getPath() + "/" + correspondingBaseNode.getName();

        boolean nodePathsAlreadyInSync = StringUtils.equals(oldBinaryFolderNodePath, newBinaryFolderNodePath);

        if (nodePathsAlreadyInSync) {
            log.debug("The node paths were already synchronized: '{}'.", oldBinaryFolderNodePath);
        } else {
            session.move(oldBinaryFolderNodePath, newBinaryFolderNodePath);
            updated = true;
        }

        Node newBinaryFolderNode = session.getNode(newBinaryFolderNodePath);

        // make sure to have proper mixins again.
        if (!newBinaryFolderNode.isNodeType("mix:referenceable")) {
            newBinaryFolderNode.addMixin("mix:referenceable");
            updated = true;
        }
        if (!newBinaryFolderNode.isNodeType("hippo:translated")) {
            newBinaryFolderNode.addMixin("hippo:translated");
            updated = true;
        }

        if (copyTranslationNodes(correspondingBaseNode, newBinaryFolderNode)) {
            updated = true;
        }

        return updated;
    }

    /**
     * Copy all the translation nodes from {@code sourceNode} to {@code targetNode}.
     * @param sourceNode
     * @param targetNode
     * @return
     * @throws RepositoryException
     */
    private boolean copyTranslationNodes(Node sourceNode, Node targetNode) throws RepositoryException {
        boolean updated = false;

        Node sourceTranslationNode;
        String sourceTranslationLanguage;
        String sourceTranslationMessage;
        Node targetTranslationNode;
        String targetTranslationMessage;

        String systemDefaultLocaleLanguage = Locale.getDefault().getLanguage();

        for (NodeIterator nodeIt = sourceNode.getNodes("hippo:translation"); nodeIt.hasNext(); ) {
            sourceTranslationNode = nodeIt.nextNode();
            sourceTranslationLanguage = sourceTranslationNode.getProperty("hippo:language").getString();
            sourceTranslationMessage = sourceTranslationNode.getProperty("hippo:message").getString();

            targetTranslationNode = getTranslationNode(targetNode, sourceTranslationLanguage);

            if (targetTranslationNode == null && StringUtils.isBlank(sourceTranslationLanguage)) {
                targetTranslationNode = getTranslationNode(targetNode, systemDefaultLocaleLanguage);
            }

            if (targetTranslationNode == null) {
                targetTranslationNode = targetNode.addNode("hippo:translation", "hippo:translation");
                targetTranslationNode.setProperty("hippo:language", sourceTranslationLanguage);
                targetTranslationNode.setProperty("hippo:message", sourceTranslationMessage);
                updated = true;
            } else {
                targetTranslationMessage = targetTranslationNode.getProperty("hippo:message").getString();
                if (!StringUtils.equals(sourceTranslationMessage, targetTranslationMessage)) {
                    targetTranslationNode.setProperty("hippo:message", sourceTranslationMessage);
                    updated = true;
                }
            }
        }

        return updated;
    }

    /**
     * Finds hippo:translation child node under {@code baseNode} by the {@code language}.
     * @param baseNode base node
     * @param language translation language
     * @return
     */
    private Node getTranslationNode(final Node baseNode, String language) {
        Node translationNode = null;
        Node node;
        String nodeLang;

        try {
            for (NodeIterator nodeIt = baseNode.getNodes("hippo:translation"); nodeIt.hasNext(); ) {
                node = nodeIt.nextNode();
                nodeLang = node.getProperty("hippo:language").getString();

                if (StringUtils.equals(language, nodeLang)) {
                    translationNode = node;
                    break;
                }
            }
        } catch (RepositoryException e) {
            log.error("Repository exception while searching for translation node by language.", e);
        }

        return translationNode;
    }

    /**
     * Finds all the mirror link compound nodes (e.g, image link nodes),
     * each of which is pointing to a node under /content/gallery/,
     * under the document handle node.
     * @param documentHandleNode the document handle node
     * @return all the mirror link compound nodes (e.g, image link nodes) under the document handle node
     */
    private Collection<Node> getLinkedBinaryFolderNodes(final Node baseNode) {
        Map<String, Node> binaryFolderNodesMap = new HashMap<String, Node>();

        try {
            final String statement = "/jcr:root"
                               + baseNode.getPath()
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
                try {
                    String subjectPath = ((HippoWorkflowEvent) event).subjectPath();

                    if (!StringUtils.startsWith(subjectPath, "/content/documents/")) {
                        log.info("Ignoring hippo event on '{}' because it's not under '/content/documents/'.", subjectPath);
                        return;
                    }

                    String subjectId = ((HippoWorkflowEvent) event).subjectId();
                    final Node subjectNode = session.getNodeByIdentifier(subjectId);

                    if ("rename".equals(event.action()) && subjectNode.isNodeType("hippostd:folder")) {
                        handleFolderRenameHippoEvent(subjectNode, subjectPath, (List<String>) event.get("arguments"));
                    } else if ("replaceAllLocalizedNames".equals(event.action()) && subjectNode.isNodeType("hippo:handle") && subjectNode.hasNode(subjectNode.getName())) {
                        handleDocumentRenameHippoEvent(subjectNode, subjectPath, (List<String>) event.get("arguments"));
                    } else if ("move".equals(event.action()) && subjectNode.isNodeType("hippo:handle") && subjectNode.hasNode(subjectNode.getName())) {
                        handleDocumentMoveHippoEvent(subjectNode, subjectPath, (List<String>) event.get("arguments"));
                    }
                } catch (RepositoryException e) {
                    log.error("Repository exception while handling rename workflow event.", e);
                }
            }
        }

    }
}
