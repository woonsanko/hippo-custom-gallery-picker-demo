What is Hippo Custom Gallery Picker Demo
========================================
This project demonstrates how you can customize the default image picker plugin components.
Just for demonstration purpose, it assumes the following simple use cases:
- If a user is editing a document (e.g, 'customgallerypickerdemo/news/2015/04/The medusa news'), and if the user is trying to add an image link in a field of the document or add an embedded image in a rich text content field, then the default binary folder should be reflecting the document node path. That is, the default binary folder path should be 'customgallerypickerdemo/news/2015/04/The medusa news/' and so the users should be able to upload the images in that folder without having to create the binary folders. If the binary folders didn't exist, it should create the binary folder automatically under the hood.
- Also, when the user renames the document or renames an interim folder, the existing binary folders must be renamed automatically.

You will probably be able to get the idea on how to customize each image picker plugin of Hippo CMS in generla even though this demo project has a very limited use case support at the moment.

Running locally
===============
This project uses the Maven Cargo plugin to run Essentials, the CMS and site locally in Tomcat.
From the project root folder, execute:

    mvn clean verify
    mvn -P cargo.run

After your project is set up, access the CMS at http://localhost:8080/cms.
See [README.txt](./README.txt) for more detail.

Test Cases
==========

Test Case 1: Custom Gallery Picker for an image link field
--------------------------------------------------------
- Visit CMS at http://localhost:8080/cms/.
- Open and edit a new article document. e.g, 'customgallerypickerdemo/news/2015/04/The medusa news'.
- Click on 'Select' button in the 'News Image' field on the right pane.
- You will see the 'customgallerypickerdemo/news/2015/04/The medusa news' binary folder be created automatically based on the current document path.
- Upload an image file by using 'Browse...' and 'Upload' buttons on the top pane.
- Select the uploaded image in the binary folder and click on 'OK' button.
- Now you will see the image selected in the field.
- Publish the document.

Test Case 2: Custom CKEditor Image Picker for an image tag
----------------------------------------------------------
- Visit CMS at http://localhost:8080/cms/.
- Open and edit a new article document. e.g, 'customgallerypickerdemo/news/2015/04/The medusa news'.
- Select the 'Content' rich text content field in the center pane.
- Click on the 'Image' toolbar button in the rich text editor (CKEditor).
- You will see the 'customgallerypickerdemo/news/2015/04/The medusa news' binary folder be created automatically based on the current document path.
- Upload an image file by using 'Browse...' and 'Upload' buttons on the top pane.
- Select the uploaded image in the binary folder and click on 'OK' button.
- Now you will see the image embedded in the rich text field.
- Publish the document.

Test Case 3: Document Renaming to synchronize Binary Folder name
----------------------------------------------------------------
- Suppose you already finished the Test Case 1 and Test Case 2 in the preceding section.
- Take the 'customgallerypickerdemo/news/2015/04/The medusa news' document offline.
- Click on 'Document/Rename...' menu item.
- Change the document name to 'The medusa news2' for instance, and click on 'Reset...' link as well. Click on 'OK' button to rename the document.
- Click on 'Edit' button to edit the document again.
- Try to update the 'News Image' field or the embedded image in the 'Content' rich text field.
- Now you will see the binary folder name has been renamed to 'customgallerypickerdemo/news/2015/04/The medusa news2' accordingly.

Test Case 4: Folder Renaming to synchronize Binary Folder name
----------------------------------------------------------------
- Suppose you already finished the Test Case 1 and Test Case 2 in the preceding section.
- Select on 'customgallerypickerdemo/news/2015/04' folder.
- Rename the folder from '04' to 'April'.
- Edit the 'customgallerypickerdemo/news/2015/04/The medusa news' document again.
- Try to update the 'News Image' field or the embedded image in the 'Content' rich text field.
- Now you will see the binary folder name has been renamed to 'customgallerypickerdemo/news/2015/April/The medusa news2' accordingly.

Custom Configurations
=====================
There are three configuration locations:
- **Custom GalleryPickerPlugin** (The image link field picker on the right pane in the Test Case 1)
  - */hippo:namespaces/hippogallerypicker/imagelink/editor:templates/_default_/root/@plugin.class = "org.example.customgallerypicker.demo.cms.plugins.BinaryPathDeterminingGalleryPickerPlugin"*
  - See [imagelink.xml](bootstrap/configuration/src/main/resources/namespaces/hippogallerypicker/imagelink.xml) for detail.
- **Custom CKEditorNodePlugin** (The embedded image picker in the CKEditor as demonstrated in the Test Case 2)
  - */hippo:namespaces/hippostd/html/editor:templates/_default_/root/@plugin.class = "org.example.customgallerypicker.demo.cms.plugins.BinaryPathDeterminingCKEditorNodePlugin"*
  - See [html.xml](bootstrap/configuration/src/main/resources/namespaces/hippostd/html.xml) for detail.
- **Custom Document Renaming Event Listener Module** (The automatic binary folder renaming module on document or folder renaming event as demonstrated in the Test Case 3 and the Test Case 4)
  - /hippo:configuration/hippo:modules/binarypathupdater (hipposys:module)
    - @hipposys:className = "org.example.customgallerypicker.demo.repository.module.BinaryPathUpdaterModule"
  - See [binarypathupdater.xml](bootstrap/configuration/src/main/resources/configuration/modules/binarypathupdater.xml) for detail.

Custom Implementation in Detail
===============================
There are three main custom components:
- **Custom GalleryPickerPlugin** (The image link field picker on the right pane in the Test Case 1)
  - [BinaryPathDeterminingGalleryPickerPlugin](cms/src/main/java/org/example/customgallerypicker/demo/cms/plugins/BinaryPathDeterminingGalleryPickerPlugin.java)
  - This component extends the default GalleryPickerPlugin in order to set the base image folder node UUID plugin configuration parameter dynamically according to the context document node path.
  - See its javadoc documentation for detail.
- **Custom CKEditorNodePlugin** (The embedded image picker in the CKEditor as demonstrated in the Test Case 2)
  - [BinaryPathDeterminingCKEditorNodePlugin](cms/src/main/java/org/example/customgallerypicker/demo/cms/plugins/BinaryPathDeterminingCKEditorNodePlugin.java)
  - This component extends the default CKEditorNodePlugin in order to set the base image folder node UUID plugin configuration parameter dynamically according to the context document node path.
  - See its javadoc documentation for detail.
- **Custom Document Renaming Event Listener Module** (The automatic binary folder renaming module on document 
  - [BinaryPathUpdaterModule](cms/src/main/java/org/example/customgallerypicker/demo/repository/module/BinaryPathUpdaterModule.java)
  - This DaemonModule component registers a HippoEvent listener in order to synchronize the binary folder name whenever user renames a document or a folder.
  - See its javadoc documentation for detail.
