package org.example.customgallerypicker.demo.cms.plugins;

import org.hippoecm.frontend.model.JcrNodeModel;
import org.hippoecm.frontend.plugin.IPluginContext;
import org.hippoecm.frontend.plugin.config.IPluginConfig;
import org.onehippo.addon.frontend.gallerypicker.GalleryPickerPlugin;

/**
 * Custom {@link GalleryPickerPlugin} implementation
 * which sets the base image folder path according to the context document path.
 * <p>
 * For example, if a document is at "/content/documents/myhippoproject/announcement/getting-started-with-hippo",
 * then the image upload base path should in turn default to the location "/content/documents/myhippoproject/announcement/getting-started-with-hippo/".
 * </p>
 */
public class BinaryPathDeterminingGalleryPickerPlugin extends GalleryPickerPlugin {

    public BinaryPathDeterminingGalleryPickerPlugin(IPluginContext context, IPluginConfig config) {
        super(context, config);
    }

    @Override
    protected IPluginConfig getPluginConfig() {
        return new BinaryPathDeterminingPluginConfig(super.getPluginConfig(), (JcrNodeModel) getModel());
    }
}
