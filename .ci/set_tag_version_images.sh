#!/bin/bash
# Copyright (c) 2017-2021 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

sed_in_place() {
    SHORT_UNAME=$(uname -s)
  if [ "$(uname)" == "Darwin" ]; then
    sed -i '' "$@"
  elif [ "${SHORT_UNAME:0:5}" == "Linux" ]; then
    sed -i "$@"
  fi
}

# only use /latest plugins so updates are smoother (old plugins are deleted from registries with each new release)
plugin_version="latest"
sed_in_place -r -e "s#che.factory.default_editor=eclipse/che-theia/.*#che.factory.default_editor=eclipse/che-theia/$plugin_version#g" ../assembly/assembly-wsmaster-war/src/main/webapp/WEB-INF/classes/che/che.properties
sed_in_place -r -e "s#che.workspace.devfile.default_editor=eclipse/che-theia/.*#che.workspace.devfile.default_editor=eclipse/che-theia/$plugin_version#g" ../assembly/assembly-wsmaster-war/src/main/webapp/WEB-INF/classes/che/che.properties
