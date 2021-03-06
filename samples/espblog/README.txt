Sling ESP blog sample
---------------------

WHY THIS SAMPLE?
----------------
This demonstrates the use of ESP server-side javascript to implement a simple
blog, with file attachments.

A java service uses JCR observation to detect incoming files (either uploaded
via the blog forms, or copied via WebDAV), and creates thumbnails of files image
files, to demonstrate observation and OSGi SCR services.

HOW TO INSTALL
--------------
Build this bundle and install it in Sling.

For example, if Sling is running on port 8888 (which happens if you start 
the launchpad/testing module with "mvn jetty:run"), this will build and 
install it:

    mvn -P autoInstallBundle clean install -Dsling.url=http://localhost:8888/system/console
    
To verify that the bundle is correctly installed:

1) http://localhost:8888/apps/espblog/html.esp must return the html.esp 
   script.

2) The console at http://localhost:8888/system/console/bundles must 
    list the bundle named "Sling - ESP blog sample" as active.    

You also need to set "allow anonymous access" to false in the 
SlingAuthenticator config, at

  http://localhost:8888/system/console/configMgr/org.apache.sling.engine.impl.auth.SlingAuthenticator

HOW TO TEST
-----------
Once "allow anonymous access" is set to false, requesting

  http://localhost:8888/

should redirect to the Sling login form at /system/sling/login.

Use username=admin password=admin to login, after which

  http://localhost:8888/

Should redirect to the "Welcome to the Sling Launchpad" index.html page.

Then, http://localhost:8888/content/espblog/*.html should display the "Sling ESP
blog sample" page, with the Home/Admin/New Post/... menu.

If you get Sling's default HTML rendition instead ("Resource dumped by 
HtmlRendererServlet") that's probably because the "path-based-rtp" bundle
is not installed. Run the above "mvn...clean install" command in the
sling/samples/path-based-rtp folder to install it, and reload the page.

Create a new post using the "New Post" menu.

The post can include an attachment, if that's an image the included
ThumbnailGeneratorService generates thumbnails in different sizes, displayed
on the post's page. This happens asynchronously, so depending on your 
machine's speed the thumbnails might only appear after a few seconds.

The ESP scripts are found under /apps/espblog in the repository.
