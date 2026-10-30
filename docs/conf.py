project = "IVOA Publishing Registry"
author = "Javastro"
extensions = ["myst_parser"]
myst_enable_extensions = ["colon_fence"]
# leave relative links such as api/index.html (added by the build, not a Sphinx document) untouched
myst_all_links_external = True
source_suffix = {".md": "markdown"}
exclude_patterns = ["_build"]
# the Javadoc (api/) is added to the site by the CI workflow
html_theme = "furo"
html_title = "IVOA Publishing Registry"
