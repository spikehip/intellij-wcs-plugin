package com.intellij.csdt;

import COM.FutureTense.Interfaces.Utilities;
import com.fatwire.cs.core.http.HostConfig;
import com.fatwire.cs.core.http.HttpAccess;
import com.fatwire.cs.core.http.Post;
import com.fatwire.cs.core.http.Response;
import com.fatwire.cs.core.realtime.DSKeyInfo;
import com.fatwire.cs.core.realtime.DataException;
import com.fatwire.csdt.service.impl.ListDSService;
import com.fatwire.csdt.service.util.CSDTServiceUtil;
import com.fatwire.csdt.valueobject.service.MapParameter;
import com.fatwire.realtime.packager.FSDataStore;
import com.fatwire.rest.beans.EnabledType;
import com.fatwire.rest.beans.SiteBean;
import com.fatwire.rest.beans.Type;
import com.fatwire.rest.beans.UserSite;
import com.fatwire.wem.sso.SSOException;
import com.intellij.configurations.WebCenterSitesPluginModuleConfigurationData;
import com.intellij.csdt.rest.RestProvider;
import com.intellij.csdt.util.Constants;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.tools.shell.Shell;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Created by NB20308 on 17/12/2015.
 */

public class CSDPUtil {

    private static Logger LOG = Logger.getInstance(CSDPUtil.class);
    private static boolean isRemote=true;
    private static WebCenterSitesPluginModuleConfigurationData webCenterSitesPluginModuleConfigurationData;

    public static void setConfigurationData(WebCenterSitesPluginModuleConfigurationData webCenterSitesPluginModuleConfigurationData) {
        CSDPUtil.webCenterSitesPluginModuleConfigurationData = webCenterSitesPluginModuleConfigurationData;
    }

    private static String map2ResourceStr(Map<String, List<String>> byResType) {
        StringBuilder resources = new StringBuilder();

        String resType;
        String ids;
        for(Iterator i$ = byResType.keySet().iterator(); i$.hasNext(); resources.append(resType + ":" + ids)) {
            resType = (String)i$.next();
            ids = "";

            String id;
            for(Iterator i$1 = ((List)byResType.get(resType)).iterator(); i$1.hasNext(); ids = ids + id) {
                id = (String)i$1.next();
                if(ids.length() > 0) {
                    ids = ids + ",";
                }
            }

            if(ids.length() <= 0) {
                ids = "*";
            }

            if(resources.length() > 0) {
                resources.append(";");
            }
        }

        return resources.toString();
    }

    public static String callImport(Map<String, List<String>> byResType, String siteNamesStr, boolean includeDeps) throws IOException, SSOException {
        String resourcesStr = map2ResourceStr(byResType);
        FSDataStore ds = getDatastore();
        List keys = CSDTServiceUtil.listDS(ds, resourcesStr, siteNamesStr);
        String username = getUserName();
        String password = getPassword();
        String host = getHost();
        String port = getPort();
        String context = getContext();
        return importByIds(resourcesStr, ds, keys, host, port, context, username, password, includeDeps);
    }
    private static String importByIds(String resources, FSDataStore ds, List<DSKeyInfo> dskeys, String host, String port, String context, String username, String password, boolean includeDeps) throws IOException, SSOException {
        String element = "OpenMarket/Xcelerate/PrologActions/Publish/csdt/CSDTService";
        String postData = "&command=import&includeDeps=" + includeDeps + (isRemote()?"&remote=true":"");
        Response res = doImport(element, postData, ds, dskeys, includeDeps, resources);
        return res.getResponseBodyAsString();
    }
    public static String callImport(String filename, boolean includeDeps) throws IOException, SSOException {
        FSDataStore ds = getDatastore();
        String ret = importByFilenames(filename, ds, includeDeps);
        if(ret.contains("lockinfoRT")) {
            String lockedby = ret.substring(ret.indexOf("lockinfoRT") + "lockinfoRT".length());
            lockedby = lockedby.substring(0, lockedby.indexOf("ofnikcolRT"));
            //MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Resource locked", filename + " cannot be edited because it is locked by " + lockedby + ". Your changes could not be saved on the server.");
        } else {
            updateHashCodeFor(filename);
        }

        return ret;
    }
    private static void updateHashCodeFor(String filename) throws IOException {
       // IPath projectPath = getProjectLocation(getProjectName());
        //String projectLocation = projectPath.toOSString();
        String fullpath = getWorkspaceFullPath() + File.separator + filename;
        File mainxml = new File(fullpath + ".main.xml");
        if(mainxml.exists()) {
            int newhash = getHashCodeForFileContents(fullpath);
            replaceLines(mainxml.getAbsolutePath(), "@hashval=", "@hashval=" + newhash);
        }

    }
    private static int getHashCodeForFileContents(String fullpath) throws IOException {
        String contents = readFully(fullpath);
        return contents.hashCode();
    }

    private static String readFully(String fullpath) throws IOException {
        File f = new File(fullpath);
        StringWriter writer = new StringWriter();
        FileInputStream in = new FileInputStream(f);

        try {
            IOUtils.copy(in, writer);
        } finally {
            IOUtils.closeQuietly(in);
        }

        return writer.toString();
    }

    private static void replaceLines(String fullpath, String match, String newVal) throws IOException {
        FileInputStream in = new FileInputStream(fullpath);
        ArrayList newLines = new ArrayList();

        try {
            Iterator out = IOUtils.readLines(in).iterator();

            while(out.hasNext()) {
                Object o = out.next();
                String line = String.valueOf(o);
                if(line.startsWith(match)) {
                    newLines.add(newVal);
                } else {
                    newLines.add(line);
                }
            }
        } finally {
            IOUtils.closeQuietly(in);
        }

        FileOutputStream out1 = new FileOutputStream(fullpath);

        try {
            IOUtils.writeLines(newLines, "\n", out1);
        } finally {
            IOUtils.closeQuietly(out1);
        }

    }

    private static String importByFilenames(String filename, FSDataStore ds, boolean includeDeps) throws IOException, SSOException {
        DSKeyInfo dskey = ds.getKeyForFilename(filename);
        String fixedfn = filename.replaceAll("\\\\", "/");
        String element = "OpenMarket/Xcelerate/PrologActions/Publish/csdt/CSDTService";
        String postData = "&command=import&filename=" + fixedfn + "&includeDeps=" + includeDeps + (isRemote()?"&remote=true":"");
        Response res = doImport(element, postData, ds, Collections.singletonList(dskey), includeDeps, null);
        return res.getResponseBodyAsString();
    }

    private static Response doImport(String element, String postData, FSDataStore ds, List<DSKeyInfo> dskeys, boolean includeDeps, String resources) throws IOException, SSOException {
        File tempfile = null;

        Response var19;
        try {
            Post e = buildPostRequest(element, postData);
            if(Utilities.goodString(resources)) {
                e.addParameter("resources", resources);
            }

            if(isRemote()) {
                Iterator it = e.getParameterNames();

                while(it.hasNext()) {
                    String out = (String)it.next();
                    String[] arr$ = e.getParameters(out);
                    int len$ = arr$.length;

                    for(int i$ = 0; i$ < len$; ++i$) {
                        String paramVal = arr$[i$];
                        e.addMultipartData(out, paramVal);
                    }
                }

                tempfile = Utilities.createTempFile(false);
                BufferedOutputStream var20 = new BufferedOutputStream(new FileOutputStream(tempfile));
                ds.stream(dskeys, includeDeps, var20);
                IOUtils.closeQuietly(var20);
                e.addMultipartData("__CSDTDSItem", "__CSDTDSItem", tempfile.getCanonicalPath());
            }

            var19 = postRequest(e);
        } catch (DataException var17) {
            var17.printStackTrace();
            throw new RuntimeException(var17);
        } finally {
            if(tempfile != null) {
                tempfile.delete();
            }

        }

        return var19;
    }

    public static boolean isRemote() {
        //return "true".equalsIgnoreCase(Activator.getDefault().getPreferenceStore().getString("fw.mode"));
         return isRemote;
    }

    public static Post buildPostRequest(String element) throws SSOException {
        return buildPostRequest(element, null);
    }

    public static Post buildPostRequest(String element, String postData) throws SSOException {
        String username = getUserName();
        String password = getPassword();
        Post post = new Post();
        String csUrl = getCSUrl() + encode(element);
        post.setUrl(csUrl);
        post.setHeader("X-CSRF-Token", TicketMaster.getTicket());
        post.addParameter("_charset_", "UTF-8");
        post.addParameter("username", username);
        post.addParameter("password", password);
        if(StringUtils.isNotBlank(postData)) {
            addPostParameters(post, postData);
        }

        return post;
    }

    public static Response postRequest(Post request) {
        String host = getHost().replace("http://","");
        String port = getPort();

        try {
            HttpAccess e = new HttpAccess(new HostConfig(host, Utilities.goodString(port)?Integer.parseInt(port):80));
            return e.execute(request);
        } catch (Exception var4) {
            //com.fatwire.csdt.util.Log.error(var4);
            throw new RuntimeException(var4);
        }
    }

    public static String getPort() {
       // return Activator.getDefault().getPreferenceStore().getString("fw.csport");
        return webCenterSitesPluginModuleConfigurationData.getPort();
    }

    public static String getHost() {
    //    return Activator.getDefault().getPreferenceStore().getString("fw.csip");
        return webCenterSitesPluginModuleConfigurationData.getHostName();
    }
    public static String getUserName() {
       // return Activator.getDefault().getPreferenceStore().getString("fw.username");
        return webCenterSitesPluginModuleConfigurationData.getUsername();
    }

    public static String getPassword() {
      //  return Activator.getDefault().getPreferenceStore().getString("fw.password");
        return webCenterSitesPluginModuleConfigurationData.getPassword();
    }

    public static String getCSUrl() {
        return getCSUrl(getBaseUrl());
    }

    public static String getCSUrl(String baseUrl) {
        return baseUrl + "/" + "ContentServer" + "?" + "pagename" + "=";
    }
    public static String getBaseUrl() {
        return getBaseUrl(getHost(), getPort(), getContext());
    }
    public static String getContext() {
       // return Activator.getDefault().getPreferenceStore().getString("fw.cscontextPath");
        return webCenterSitesPluginModuleConfigurationData.getWebContextPath();
    }

    public static String getBaseUrl(String host, String port, String context) {
       port = StringUtils.equals("80", port)?"":":" + port;
        Matcher m = Constants.REGEX_STRIP_LEADING_TRAILING_SLASH.matcher(context);
        if(m.find()) {
            context = m.group();
        }

        String base = "http://" + host + port + "/" + context;
        URL u = null;

        try {
            u = new URL(base);
            return u.toString();
        } catch (MalformedURLException var7) {
            throw new IllegalArgumentException(var7);
        }
       // return   WebCenterSitesPluginModuleConfigurationData.getHostName()+":"+getPort()+"/cs";
    }

    public static void addPostParameters(Post request, String postData) {
        String[] entries = postData.split("&");
        String[] arr$ = entries;
        int len$ = entries.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            String each = arr$[i$];
            String[] entry = each.split("=");
            String key = entry[0];
            if(StringUtils.isNotBlank(key)) {
                String value = "";
                if(entry.length > 0) {
                    value = entry[1];
                    request.addParameter(key, value);
                }
            }
        }

    }

    public static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException var2) {
            //com.fatwire.csdt.util.Log.error("Error encoding string: " + s, var2);
            throw new RuntimeException("Error encoding string: " + s, var2);
        }
    }
    public static FSDataStore getDatastore() {
        File workspaceFile = (new File(getWorkspaceFullPath()));
        String basepath = workspaceFile.getName();
        String storeName = basepath;
        if (!basepath.endsWith("envision")) {
            basepath = basepath + "/envision";
        }
        while (!basepath.endsWith("envision")) {
            basepath = workspaceFile.getParent();
            workspaceFile = workspaceFile.getParentFile();
            storeName = workspaceFile.getName() + File.separator + storeName;
        }
        LOG.info("basepath: " + basepath);
        LOG.info("getDatastoreName: " + getDatastoreName());
        return FSDataStore.getInstance(null, workspaceFile.getParent(), storeName.replace("envision" + File.separator, ""));
    }
    public static String getDatastoreName() {
        //return Activator.getDefault().getPreferenceStore().getString("fw.datastorename");
        return webCenterSitesPluginModuleConfigurationData.getDataStoreName();
    }
    public static String getWorkspaceFullPath() {
        //IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        //return root.getProject(getProjectName()).getLocationURI().getRawPath();
        LOG.info("Workspace: " + webCenterSitesPluginModuleConfigurationData.getWorkspace());
        return webCenterSitesPluginModuleConfigurationData.getWorkspace();
    }

    public static String buildQuery(String[]... paramMap) {
        if(paramMap != null && paramMap.length >= 1) {
            StringBuilder b = new StringBuilder();
            boolean first = true;
            String[][] arr$ = paramMap;
            int len$ = paramMap.length;

            for(int i$ = 0; i$ < len$; ++i$) {
                String[] entry = arr$[i$];
                if(entry.length > 0) {
                    String key = entry[0];
                    if(StringUtils.isNotBlank(key)) {
                        if(first) {
                            first = false;
                        } else {
                            b.append("&");
                        }

                        b.append(key).append("=");
                        if(entry.length > 1) {
                            for(int i = 1; i < entry.length; ++i) {
                                if(i != 1) {
                                    b.append("&");
                                    b.append(key).append("=");
                                }

                                b.append(entry[i]);
                            }
                        }
                    }
                }
            }

            return b.toString();
        } else {
            return null;
        }
    }

    public static String generateResponse(InputStream is) throws IOException {
        BufferedReader respreader = new BufferedReader(new InputStreamReader(is));
        String resp = "";

        String response;
        for(response = ""; (resp = respreader.readLine()) != null; response = response + resp) {
        }

        respreader.close();
        is.close();
        return response;
    }

    public static ArrayList<String[]> getDSListing() {
        ArrayList retval = new ArrayList();
        FSDataStore ds = getDatastore();
        String response = (new ListDSService()).listKeys(ds, null, null);
        retval.addAll(_parseResponse("__BEGIN__" + response + "__END__"));
        return retval;
    }

    public static ArrayList<String[]> getCSListing(String[] types) throws IOException, SSOException {
        ArrayList retval = new ArrayList();
        String listStr = "";
        String[] element = types;
        int postData = types.length;

        for(int response = 0; response < postData; ++response) {
            String e = element[response];
            if(listStr.length() > 0) {
                listStr = listStr + ";";
            }

            listStr = listStr + e + ":*";
        }

        String var9 = "OpenMarket/Xcelerate/PrologActions/Publish/csdt/CSDTService";
        String var10 = "&resources=" + listStr + "&command=listcs" + "&datastore=" + getDatastoreName();

        String var11;

            var11 = post(var9, var10);

        retval.addAll(_parseResponse(var11));
        return retval;

    }
    public static String post(String element, String postData) throws SSOException, IOException {
        Post request = buildPostRequest(element, postData);
        InputStream respStream = postRequest(request).getResponseBodyAsStream();
        return generateResponse(respStream);
    }

    public static String post(Post request) throws IOException {
        InputStream respStream = postRequest(request).getResponseBodyAsStream();
        return generateResponse(respStream);
    }

    private static List<DSKeyInfo> _filterKeys(Set<String> types, List<DSKeyInfo> keys) {
        ArrayList ret = new ArrayList();
        Iterator i$ = types.iterator();

        while(true) {
            String type;
            do {
                if(!i$.hasNext()) {
                    return ret;
                }

                type = (String)i$.next();
            } while(type.startsWith("@"));

            Iterator i$1 = keys.iterator();

            while(i$1.hasNext()) {
                DSKeyInfo key = (DSKeyInfo)i$1.next();
                if(key.getName().contains(type)) {
                    ret.add(key);
                }
            }
        }
    }

    public static String callExport(Map<String, List<String>> byResType, String siteNamesStr, boolean includeDeps) {
        String resourcesStr = map2ResourceStr(byResType);
        String element = "OpenMarket/Xcelerate/PrologActions/Publish/csdt/Stream";
        String postData = "&command=export&includeDeps=" + includeDeps + (isRemote()?"&remote=true":"");
        FSDataStore ds = getDatastore();
        File tempfile = null;

        try {
            Post e = buildPostRequest(element, postData);
            e.addParameter("resources", resourcesStr);
            if(isRemote() && Boolean.parseBoolean(System.getProperty("csdt.uploadDS", "false"))) {
                Iterator response = e.getParameterNames();

                while(true) {
                    if(!response.hasNext()) {
                        List var21 = _filterKeys(byResType.keySet(), ds.keys());
                        tempfile = Utilities.createTempFile(false);
                        BufferedOutputStream var23 = new BufferedOutputStream(new FileOutputStream(tempfile));
                        ds.stream(var21, includeDeps, var23);
                        IOUtils.closeQuietly(var23);
                        LOG.info("var23: " + var23);
                        e.addMultipartData("__CSDTDSItem", "__CSDTDSItem", tempfile.getCanonicalPath());
                        break;
                    }

                    String respStream = (String)response.next();
                    String[] out = e.getParameters(respStream);
                    int len$ = out.length;

                    for(int i$ = 0; i$ < len$; ++i$) {
                        String paramVal = out[i$];
                        e.addMultipartData(respStream, paramVal);
                    }
                }
            }

            Response var20 = postRequest(e);
            InputStream var22 = var20.getResponseBodyAsStream();
            ds.ingest(var22);
        } catch (Exception e) {
            LOG.error("Exception " + e);
            throw new RuntimeException(e);
        } finally {
            if(tempfile != null) {
                tempfile.delete();
            }

        }

        return "";
    }

    public static String callExport(String assetTag) {
        String[] split1 = assetTag.split(".*<assetid>");
        String[] split2 = split1[1].split("</assetid>");
        String[] typeId = split2[0].split(":");
        return callExport(typeId[0], typeId[1]);
    }

    public static String callExport(String type, String id) {
        HashMap map = new HashMap();
        map.put(type, Collections.singletonList(id));
        return callExport(map, null, false);
    }

    private static ArrayList<String[]> _parseResponse(String response) {
        ArrayList retval = new ArrayList();
        if(response != null && response.indexOf("__BEGIN__") >= 0) {
            if(null != response && response.length() > 0) {
                String[] temp = response.split("__BEGIN__");
                if(temp != null && temp.length > 1) {
                    temp = temp[1].split("__END__");
                }

                if(null != temp && temp.length > 0) {
                    String msg = temp[0];
                    String[] arr$ = msg.split(":_:_:");
                    int len$ = arr$.length;

                    for(int i$ = 0; i$ < len$; ++i$) {
                        String row = arr$[i$];
                        if(row != null && row.length() > 0) {
                            String[] rowdata = row.split(":::___:::");
                            retval.add(rowdata);
                        }
                    }
                }
            }

            return retval;
        } else {
            return retval;
        }
    }

    public static String[] getUserSitenames() throws SSOException {
        return getUserSitenames(1);
    }

    public static String[] getUserSitenames(int sortOrder) throws SSOException {
        List userSites = RestProvider.getUserSites(getUserName());
        List siteNames = getNames(userSites, new CSDPUtil.NameExtractor() {

            @Override
            public String getName(Object userSite) {
                return ((UserSite) userSite).getSite();
            }
        });
        sort(sortOrder, siteNames);
        return (String[]) siteNames.toArray(new String[0]);
    }

    public static <T> List<String> getNames(List<T> source, CSDPUtil.NameExtractor<T> n) {
        ArrayList names = new ArrayList();
        if (source != null) {
            Iterator i$ = source.iterator();

            while (i$.hasNext()) {
                T each = (T) i$.next();
                names.add(n.getName(each));
            }
        }

        return names;
    }

    public static String[] getACLS() {
        ArrayList acls = new ArrayList();
        acls.add("Any");
        acls.addAll(RestProvider.getACLs());
        return (String[]) acls.toArray(new String[0]);
    }

    public static String getRESTServletUrl() {
        return getRESTServletUrl(getBaseUrl());
    }

    public static String getRESTServletUrl(String baseUrl) {
        return baseUrl + "/" + "REST";
    }

    public static void sort(int sortOrder, List<String> source) {
        if (sortOrder != 0) {
            Collections.sort(source);
            if (sortOrder < 0) {
                Collections.reverse(source);
            }
        }

    }

    public static <T> String delimited(CharSequence delimiter, List<T> values) {
        return delimited(delimiter, values, null);
    }

    public static <T> String delimited(CharSequence delimiter, List<T> values, CSDPUtil.NameExtractor<T> extractor) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        T each;
        if (values != null) {
            for (Iterator i$ = values.iterator(); i$.hasNext(); sb.append(extractor == null ? each : extractor.getName(each))) {
                each = (T) i$.next();
                if (first) {
                    first = false;
                } else {
                    sb.append(delimiter);
                }
            }
        }

        return sb.toString();
    }

    public static String buildPagenameFromRootElement(String siteName, String rootElementName) {
        StringBuilder sb = new StringBuilder();
        sb.append(siteName);
        if (StringUtils.isBlank(rootElementName)) {
            return "";
        } else {
            if (!rootElementName.startsWith("/")) {
                sb.append("/");
            }

            sb.append(rootElementName);
            return sb.toString();
        }
    }

    public static String buildRootElement(String templateName, String assetType) {
        String assetTypeName = isTypeless(assetType) ? "" : assetType;
        return StringUtils.isBlank(templateName) ? "" : assetTypeName + "/" + templateName;
    }

    public static boolean isTypeless(String assetType) {
        return StringUtils.isBlank(assetType);
    }

    public static String buildStoragePath(String rootElementName, com.fatwire.csdt.valueobject.enumeration.ElementFileType elementType) {
        if (!StringUtils.isBlank(rootElementName) && elementType != null) {
            String elementName = rootElementName;
            if (rootElementName.startsWith("/")) {
                elementName = rootElementName.substring(1);
            }

            String storagePath = elementName + elementType.extension();
            return sanitize(storagePath);
        } else {
            return "";
        }
    }

    public static String sanitize(String raw) {
        raw = raw.replaceAll("/\\s*/+", "/");
        return raw.replace("/", File.separator);
    }

    public static List<String> getAllAssetTypes() throws SSOException {
        return getNames(RestProvider.getAllAssetTypes(), new CSDPUtil.NameExtractor() {
            @Override
            public String getName(Object type) {
                return ((Type) type).getName();
            }

        });
    }

    public static String[] addDefault(String[] source, String defaultValue) {
        if (defaultValue == null) {
            return source;
        } else {
            ArrayList list = new ArrayList(Arrays.asList(source));
            list.add(0, defaultValue);
            return (String[]) list.toArray(new String[0]);
        }
    }

    public static String[] getEnabledTypesForSite(String siteName) throws SSOException {
        List<String> allTypeNames = getAllAssetTypes();
        List<String> enabledTypeNames = getNames(RestProvider.getEnabledTypes(siteName), new CSDPUtil.NameExtractor() {
            @Override
            public String getName(Object type) {
                return ((EnabledType) type).getName();
            }
        });
        ArrayList types = new ArrayList(enabledTypeNames);
        types.retainAll(allTypeNames);
        return (String[]) types.toArray(new String[0]);
    }

    public static String[] getAssetSubTypes(List<Type> types, String assetType) {
        if (StringUtils.isNotBlank(assetType)) {
            Iterator i$ = types.iterator();

            while (i$.hasNext()) {
                Type each = (Type) i$.next();
                if (assetType.equals(each.getName())) {
                    return each.getSubtypes().toArray(new String[0]);
                }
            }
        }

        return new String[0];
    }

    /*public static URL getRootFileUrl() throws MalformedURLException, URISyntaxException {
        return getClass().getClassLoader().getResource("").toURI().toURL();
       // return webCenterSitesPluginModuleConfigurationData.getContextPath();
        //return Activator.getDefault().getBundle().getEntry("/");
    }*/
    public static String getPubId(String pubName) {
        return String.valueOf(RestProvider.getSite(pubName).getId());
    }

    public static String getPubName(String pubId) throws SSOException {
        if (StringUtils.isNotBlank(pubId)) {
            List usersites = RestProvider.getUserSites(getUserName());
            Iterator i$ = usersites.iterator();

            while (i$.hasNext()) {
                UserSite each = (UserSite) i$.next();
                String sitename = each.getSite();
                SiteBean siteBean = RestProvider.getSite(sitename);
                if (pubId.equals(String.valueOf(siteBean.getId()))) {
                    return sitename;
                }
            }
        }

        return null;
    }

    public static String buildMapParameterString(MapParameter p) {
        StringBuilder sb = new StringBuilder();
        if (p != null) {
            sb.append(p.getKey()).append("#");
            sb.append(p.getValue()).append("#");
            sb.append(p.getType().parameter()).append("#");
            String siteId = getPubId(p.getSite());
            sb.append(siteId);
        }

        return sb.toString();
    }


    public static String buildRootElement(String templateName) {
        return buildRootElement(templateName, null);
    }

    public static boolean deleteByFile(String fileName, Shell shell) {
        ArrayList list = new ArrayList();
        list.add(fileName);
        return deleteByFile(list, shell);
    }

    public static boolean deleteByFile(ArrayList<String> fileNames, Shell shell) {
        boolean success = true;
        if (null != fileNames && !fileNames.isEmpty()) {
//            DialogWrapper dialog = new SimpleDialog();

////            MessageDialogWithToggle diag = MessageDialogWithToggle.openOkCancelConfirm(shell, "Resource Delete", "Selected assets will be marked VOID and related files will be deleted. Are you sure you want to delete selected resources(s)?", "Remove voided assets on disk", false, (IPreferenceStore)null, (String)null);
////            if(diag.getReturnCode() == 0) {
//                Iterator i$ = fileNames.iterator();
//
//                while(true) {
//                    String fileName;
//                    do {
//                        do {
//                            if(!i$.hasNext()) {
//                                if(success) {
////                                    MessageDialog.openInformation(shell, "Info ", "Delete Successful");
////                                    refreshIResource();
//                                }
//
//                                return success;
//                            }
//
//                            fileName = (String)i$.next();
//                        } while(fileName == null);
//                    } while(fileName.trim().length() <= 0);
//
////                    IPath projectPath = getProjectLocation(getProjectName());
////                    String projectLocation = projectPath.toOSString();
//                    if(fileName.startsWith(projectLocation)) {
//                        fileName = fileName.substring(projectLocation.length());
//                    }
//
//                    FSDataStore ds = getDatastore();
//                    DSKeyInfo dsKey = ds.getKeyForFilename(fileName);
//                    String element = "OpenMarket/Xcelerate/PrologActions/Publish/csdt/CSDTService";
//                    String postData = "&command=delete&dskeys=" + dsKey.getName();
//
//                    try {
//                        String ex = post(element, postData);
//                        if(!ex.contains("Success")) {
////                            MessageDialog.openInformation(shell, "Delete Unsuccessful for " + fileName, ex != null?ex.trim():"");
//                            success = false;
//                        } else if(diag.getToggleState()) {
//                            ds.remove(dsKey.getName());
//                        } else {
//                            String[] split1 = ex.split(".*<assetid>");
//                            String[] split2 = split1[1].split("</assetid>");
//                            String[] assetIds = split2[0].split(",");
//                            HashMap map = new HashMap();
//                            String[] arr$ = assetIds;
//                            int len$ = assetIds.length;
//
//                            for(int i$1 = 0; i$1 < len$; ++i$1) {
//                                String assetId = arr$[i$1];
//                                String[] typeId = assetId.split(":");
//                                Object ids = (List)map.get(typeId[0]);
//                                if(ids == null) {
//                                    ids = new ArrayList();
//                                }
//
//                                ((List)ids).add(typeId[1]);
//                                map.put(typeId[0], ids);
//                            }
//
//                            callExport(map, (String)null, false);
//                        }
//                    } catch (Exception var23) {
//                        success = false;
//                        var23.printStackTrace();
////                        MessageDialog.openError(shell, "Delete Failed", "Could not delete the asset associated with " + fileName + ". Please check the log for details.");
//                    }
//                }
//            }
        }

        return success;
    }

    public interface NameExtractor<T> {
        String getName(T var1);
    }
}



