project = "IVOA Publishing Registry"
author = "Javastro"
extensions = ["myst_parser"]
myst_enable_extensions = ["colon_fence"]
source_suffix = {".md": "markdown"}
exclude_patterns = ["_build"]
# the Javadoc (api/) is added to the site by the CI workflow
suppress_warnings = ["myst.xref_missing"]
html_theme = "furo"
html_title = "IVOA Publishing Registry"
