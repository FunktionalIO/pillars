<%
    // execute externalized script to get the menu
    new GroovyShell(getClass().getClassLoader(),
        new Binding([
            published_content: published_content,
            content: content,
            config: config
        ])
    ).evaluate(
        new File(config.sourceFolder,"groovy/menu.groovy").text
    )
%>
<nav class="header">
    <a href="${content.rootpath}"><img src="${content.rootpath}images/logo_without_name.svg" width="43"></a>
    <h1 class="logo">Pillars <span class="logo__thin">Doc</span></h1>
    <ul class="menu">
        <div class="menu__item toggle"><span></span></div>
        <li class="menu__item"><a href="${content.rootpath}" class="link link--dark"><i class="fa fa-home"></i> Home</a></li>
<%
        content.newEntries.each { entry ->
%>
        <li class="menu__item"><a href="${entry.href}" class="link link--dark"><i class="fa fa-book"></i> ${entry.title}</a></li>
<%
        }
%>
        <li class="menu__item"><a href="${content.rootpath}api/" class="link link--dark"><i class="fa fa-cogs"></i> API</a></li>
        <li class="menu__item"><a href="https://github.com/FunktionalIO/pillars/" class="link link--dark"><i class="fa fa-github"></i> Github</a></li>
        <li class="menu__item">
            <form action="${content.rootpath}search.html">
                <input aria-label="Search this site…" autocomplete="off" class="form-control td-search-input"
                       placeholder="Search this site…" type="search" name="q">
            </form>
        </li>
    </ul>
</nav>
