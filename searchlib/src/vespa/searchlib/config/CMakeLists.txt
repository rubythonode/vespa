# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_sconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(searchlib_sconfig translogserver.def)
install(FILES translogserver.def RENAME searchlib.translogserver.def DESTINATION var/db/vespa/config_server/serverdb/classes)
