package io.openems.edge.simulator.pythonBridge;

import io.openems.edge.common.channel.Channel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ChannelUpdater {

    private static List<Channel<?>> channels = new ArrayList<>();

    public static void addChannels(Collection<Channel<?>> pChannels){
        channels.addAll(pChannels);
    }

    public static void resetChannels(){
        channels = new ArrayList<>();
    }

    public static void updateChannels(){
        for(Channel<?> channel : channels) {
            channel.nextProcessImage();
        }
    }
}
