<head>
    <%
        //let's fix the context root
        if (!content.rootpath) {
            //if we are in the main folder, we need no rootpath
            content.rootpath = ''
            //but if we are deeper in the folder structure...
            if (content.sourceuri) {
                content.rootpath = '../' * (content.sourceuri?.split('/')?.size() - 1)
            }
        }
        //this is mainly a fix for the imagesdir which is set to /images
        content.body = content.body?.replaceAll('src="/', 'src="' + content.rootpath)
    %>
    <!-- sourceuri: ${content.sourceuri} -->
    <meta content="text/html; charset=UTF-8" http-equiv="Content-Type">

    <meta content="width=device-width, initial-scale=1, shrink-to-fit=no" name="viewport">
    <meta content="INDEX, FOLLOW" name="ROBOTS">

    <title>${content.title ? content.title : config.site_title}</title>

    <meta content="Pillars" property="og:title">
    <meta content="Build your backend service in Scala 3 the easy way" property="og:description">
    <meta content="website" property="og:type">
    <meta content="Pillars" property="og:site_name">
    <meta content="Pillars" itemprop="name">
    <meta content="Build your backend service in Scala 3 the easy way" itemprop="description">
    <meta content="summary" name="twitter:card">
    <meta content="@rlemaitre.com" name="twitter:creator">
    <meta content="Pillars" name="twitter:title">
    <meta content="Build your backend service in Scala 3 the easy way" name="twitter:description">
    <meta property="og:url" content="https://pillars.dev/" />
    <meta property="og:title" content="Pillars" />
    <meta property="og:description" content="Build your backend service in Scala 3 the easy way" />
    <meta property="og:image" content="https://pillars.dev/images/logo.svg" />

    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
    <link href="https://fonts.googleapis.com/css?family=Nunito+Sans:300,400,600,700,800,900" rel="stylesheet">
    <link rel="stylesheet" href="${content.rootpath}css/asciidoctor.css">

    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
    <link rel="stylesheet" href="${content.rootpath}css/scribbler-global.css">
    <% if (content.type == 'masterindex') { %>
        <link rel="stylesheet" href="${content.rootpath}css/scribbler-landing.css">
    <% } else { %>
        <link rel="stylesheet" href="${content.rootpath}css/scribbler-doc.css">
    <% } %>
    <link rel="author" href="${content.rootpath}/humans.txt">

    <!-- favicon generated with https://www.favicon-generator.org/ -->
    <link rel="icon" type="image/svg+xml" href="${content.rootpath}favicon.svg">
    <link rel="icon" type="image/png" href="${content.rootpath}favicon.png">
    <meta name="theme-color" content="#0a1322">
</head>
