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

public class BinaryPathUpdaterModule implements DaemonModule {

    private static Logger log = LoggerFactory.getLogger(BinaryPathUpdaterModule.class);

    private Session session;
    private HippoDocumentRenameEventListener documentRenameEventListener;

    @Override
    public void initialize(Session session) throws RepositoryException {
        this.session = session;

        documentRenameEventListener = new HippoDocumentRenameEventListener();
        HippoServiceRegistry.registerService(documentRenameEventListener, HippoEventBus.class);
    }

    @Override
    public void shutdown() {
        if (documentRenameEventListener != null) {
            HippoServiceRegistry.unregisterService(documentRenameEventListener, HippoEventBus.class);
        }
    }

    protected void handleDocumentRenameHippoEvent(final String documentHandleUuid) {
        try {
            final Node documentHandleNode = session.getNodeByIdentifier(documentHandleUuid);
            final String documentHandleRelPath = StringUtils.removeStart(documentHandleNode.getPath(), "/content/documents/");
            final String documentHandleParentRelPath = StringUtils.removeStart(documentHandleNode.getParent().getPath(), "/content/documents/");

            final Collection<Node> binaryFolderNodes = getLinkedBinaryFolderNodes(documentHandleNode);
            String binaryFolderRelPath;
            String binaryFolderParentRelPath;

            boolean anyMoved = false;

            for (Node binaryFolderNode : binaryFolderNodes) {
                binaryFolderRelPath = StringUtils.removeStart(binaryFolderNode.getPath(), "/content/gallery/");
                binaryFolderParentRelPath = StringUtils.removeStart(binaryFolderNode.getParent().getPath(), "/content/gallery/");

                if (StringUtils.equals(binaryFolderParentRelPath, documentHandleParentRelPath)) {
                    if (!StringUtils.equals(binaryFolderRelPath, documentHandleRelPath)) {
                        // Now rename the binary folder name according to the document handle node name here...
                        session.move(binaryFolderNode.getPath(), binaryFolderNode.getParent().getPath() + "/" + documentHandleNode.getName());
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
                session.refresh(true);
            } catch (RepositoryException re) {
                log.error("Failed to refresh the session.", re);
            }
        }
    }

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
