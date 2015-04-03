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

public class BinaryPathDeterminingPluginConfig extends AbstractPluginDecorator {

    private static Logger log = LoggerFactory.getLogger(BinaryPathDeterminingPluginConfig.class);

    private final JcrNodeModel contextFieldNodeModel;

    public BinaryPathDeterminingPluginConfig(IPluginConfig upstream, final JcrNodeModel contextFieldNodeModel) {
        super(upstream);
        this.contextFieldNodeModel = contextFieldNodeModel;
    }

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

    @Override
    protected Object decorate(Object object) {
        return object;
    }

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

    private Node createBinaryFolderNodeFor(final Node contextDocumentHandleNode) throws RepositoryException {
        String documentHandleRelPath = StringUtils.removeStart(contextDocumentHandleNode.getPath(), "/content/documents/");
        final Session session = UserSession.get().getJcrSession();

        Node binaryFolderNode = session.getRootNode().getNode("content/gallery");
        Node documentFolderNode = session.getRootNode().getNode("content/documents");

        Node documentFolderTranslationNode;
        Node binaryFolderTranslationNode;
        String translationLanguage;
        String translationMessage;

        String [] nodeNames = StringUtils.split(documentHandleRelPath, "/");

        boolean changed = false;

        try {
            for (String nodeName : nodeNames) {
                documentFolderNode = documentFolderNode.getNode(nodeName);

                if (binaryFolderNode.hasNode(nodeName)) {
                    binaryFolderNode = binaryFolderNode.getNode(nodeName);
                } else {
                    binaryFolderNode = binaryFolderNode.addNode(nodeName, "hippogallery:stdImageGallery");
                    binaryFolderNode.addMixin("mix:referenceable");
                    binaryFolderNode.addMixin("hippo:translated");
                    binaryFolderNode.setProperty("hippostd:foldertype", new String [] { "new-image-folder" });
                    binaryFolderNode.setProperty("hippostd:gallerytype", new String [] { "hippogallery:imageset" });

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
