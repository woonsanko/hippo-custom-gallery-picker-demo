package org.example.customgallerypicker.demo.cms.plugins;

import org.hippoecm.frontend.model.JcrNodeModel;
import org.hippoecm.frontend.plugin.IPluginContext;
import org.hippoecm.frontend.plugin.config.IPluginConfig;
import org.hippoecm.frontend.plugin.config.impl.AbstractPluginDecorator;
import org.hippoecm.frontend.plugins.ckeditor.CKEditorNodePlugin;

/**
 * Custom {@link CKEditorNodePlugin} implementation
 * which sets the base image folder path according to the context document path.
 * <p>
 * For example, if a document is at "/content/documents/myhippoproject/announcement/getting-started-with-hippo",
 * then the image upload base path should in turn default to the location "/content/documents/myhippoproject/announcement/getting-started-with-hippo/".
 * </p>
 */
public class BinaryPathDeterminingCKEditorNodePlugin extends CKEditorNodePlugin {

    public BinaryPathDeterminingCKEditorNodePlugin(IPluginContext context, IPluginConfig config) {
        super(context, config);
    }

    @Override
    protected IPluginConfig getPluginConfig() {
        final IPluginConfig originalConfig = super.getPluginConfig();

        return new AbstractPluginDecorator(originalConfig) {

            @Override
            public IPluginConfig getPluginConfig(Object key) {
                IPluginConfig childConfig = null;

                if (CKEditorNodePlugin.CONFIG_CHILD_IMAGE_PICKER.equals(key)) {
                    childConfig = new BinaryPathDeterminingPluginConfig(originalConfig, (JcrNodeModel) getModel());
                }

                if (childConfig == null) {
                    childConfig = super.getPluginConfig(key);
                }

                return childConfig;
            }

            @Override
            protected Object decorate(Object object) {
                return object;
            }
        };
    }

}
