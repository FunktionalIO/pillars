<!doctype html>
<html lang="en">
<%include "header.gsp"%>
<body>
<%include "page_menu.gsp"%>
<div class="wrapper">
	<aside class="doc__nav">
		<%include "submenu.gsp"%>
	</aside>
	<article class="doc__content">
		<%include "main.gsp"%>
    <script src="https://giscus.app/client.js"
            data-repo="FunktionalIO/pillars"
            data-repo-id="R_kgDOK-Le0g"
            data-category="Comments"
            data-category-id="DIC_kwDOK-Le0s4Ccs4S"
            data-mapping="pathname"
            data-strict="0"
            data-reactions-enabled="1"
            data-emit-metadata="0"
            data-input-position="top"
            data-theme="light"
            data-lang="en"
            data-loading="lazy"
            crossorigin="anonymous"
            async>
    </script>
	</article>
	<aside class="doc__nav pop-out">
		<%include "rightcolumn.gsp" %>
	</aside>
</div>
<%include "footer.gsp"%>
</body>
</html>
