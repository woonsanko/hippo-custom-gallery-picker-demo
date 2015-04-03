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

import org.hippoecm.frontend.model.JcrNodeModel;
import org.hippoecm.frontend.plugin.IPluginContext;
import org.hippoecm.frontend.plugin.config.IPluginConfig;
import org.onehippo.addon.frontend.gallerypicker.GalleryPickerPlugin;

/**
 * Custom {@link GalleryPickerPlugin} implementation
 * which sets the base image folder path according to the context document path.
 * <p>
 * For example, if a document is at "/content/documents/myhippoproject/announcement/getting-started-with-hippo",
 * then the image upload base path should be in turn to the location at
 * "/content/gallery/myhippoproject/announcement/getting-started-with-hippo/" by default.
 * </p>
 * <p>
 * Note: you should set <code>/hippo:namespaces/hippogallerypicker/imagelink/editor:templates/_default_/root/@plugin.class</code>
 *       to the FQCN of this class to apply this custom picker plugin.
 * </p>
 */
public class BinaryPathDeterminingGalleryPickerPlugin extends GalleryPickerPlugin {

    /**
     * Constructor simply invoking super constructor.
     * @param context plugin context
     * @param config plugin config
     */
    public BinaryPathDeterminingGalleryPickerPlugin(IPluginContext context, IPluginConfig config) {
        super(context, config);
    }

    /**
     * {@inheritDoc}
     * <p></p>
     * Return a decorated {@code IPluginConfig},
     * which should return the original value from the original upstream plugin configuration by default,
     * but should return a different value based on the current context document path.
     */
    @Override
    protected IPluginConfig getPluginConfig() {
        return new BinaryPathDeterminingPluginConfig(super.getPluginConfig(), (JcrNodeModel) getModel());
    }
}
