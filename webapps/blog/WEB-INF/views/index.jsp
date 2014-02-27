<%@ page import="
        com.scooterframework.admin.EnvConfig,
        com.scooterframework.web.controller.MainActionServlet,
        com.scooterframework.web.util.W"
%>
<h2><%=W.label("welcome.message")%></h2>
<h4>Have a fun drive!</h4>
<h3><a href="/blog/posts">Enter eBlog</a></h3>


<h2 class="sectionTitle">More fun</h2>

<b><%=W.labelLink("Site info", "/admin/site")%></b>: view application environment information
<br/><br/>

<b><%=W.labelLink("Site manager", "/admin/files/list")%></b>: manage deployed files and folders (add/view/edit/replace/copy/delete/rename)
<br/><br/>

<%if (MainActionServlet.isUsingRestfulProcessor()) {%>
<b><%=W.labelLink("Browse routes", "/admin/routes")%></b>: view all routes supported by this site
<%} else {%>
<b>Browse routes</b>: You need to choose <tt>com.scooterframework.web.controller.RestfulRequestProcessor</tt> 
as the processor in <tt>web.xml</tt>
<%}%>
<br/><br/>

<%if (MainActionServlet.isUsingRestfulProcessor() && EnvConfig.getInstance().allowDataBrowser()) {%>
<b><%=W.labelLink("Browse databases", "/admin/databases")%></b>: see what you have in your data store
<%} else {%>
<b>Browse databases</b>: You need to choose <tt>com.scooterframework.web.controller.RestfulRequestProcessor</tt> 
as the processor in <tt>web.xml</tt> and also set <tt>allow.databrowser=true</tt> in environment.properties file.
<%}%>
<br/><br/>

<%if (MainActionServlet.isUsingRestfulProcessor() && EnvConfig.getInstance().allowDataBrowser()) {%>
<b><%=W.labelLink("SQL Window", "/admin/sqlwindow")%></b>: run ad-hoc SQL statement
<%} else {%>
<b>Run ad-hoc SQL</b>: You need to choose <tt>com.scooterframework.web.controller.RestfulRequestProcessor</tt> 
as the processor in <tt>web.xml</tt> and also set <tt>allow.databrowser=true</tt> in environment.properties file.
<%}%>
<br/><br/>