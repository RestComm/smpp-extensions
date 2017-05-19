package org.restcomm.smpp.extension;

import org.jboss.as.controller.AbstractRemoveStepHandler;

class SmppMbeanRemove extends AbstractRemoveStepHandler {

    static final SmppMbeanRemove INSTANCE = new SmppMbeanRemove();

    private SmppMbeanRemove() {
    }
}