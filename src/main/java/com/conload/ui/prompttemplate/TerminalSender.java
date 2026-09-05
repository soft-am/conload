package com.conload.ui.prompttemplate;

@FunctionalInterface
public interface TerminalSender {
    void send(String data, Runnable onComplete);
}
