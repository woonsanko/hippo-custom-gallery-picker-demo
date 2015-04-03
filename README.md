What is Hippo Custom Gallery Picker Demo
========================================
TODO

Running locally
===============
This project uses the Maven Cargo plugin to run Essentials, the CMS and site locally in Tomcat.
From the project root folder, execute:

    mvn clean verify
    mvn -P cargo.run

After your project is set up, access the CMS at http://localhost:8080/cms.
Logs are located in target/tomcat7x/logs

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

Test Case 3: Document Renaming to synchronize Binary Folder name
----------------------------------------------------------------
- TODO1
- TODO2

Custom Configurations
=====================
TODO

Custom Implementation in Detail
===============================
TODO

