package org.homework.common;

/**
 * 存储所有服务器结点和需要共享的配置信息
 */
public class ServerInfo {

    public final String introducerIp = "127.0.0.1";

    //监听新节点加入与客户端查询
    public static final int introducerPort = 2718;

    //监听节点的变化
    public final int introducerListPort = 2720;

    public final int broadcastPort = 5500;

    public final int heartbeatingPort = 8765;


    //todo 这个端口应该是节点端口
    public static final int gossipPort = 9010;

    public static final int queryPort = 20527;

    //连续2次未回应，即认为断开连接
    public final int tolerate = 2;

    //丢包率, 默认为0
    public final double loss = 0;

}
