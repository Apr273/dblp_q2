package org.homework.entity;

import java.util.ArrayList;

/**
 * 组成员列表实体类
 */
public class GroupMemberList {
    public static ArrayList<MemberInfo> members=new ArrayList<>();

    public class MemberInfo{
        String timestamp;
        public Integer port;
        MemberInfo(String timestamp,Integer port){
            this.timestamp=timestamp;
            this.port=port;
        }
    }

    /**
     * 显示组成员列表
     */
    public void memberShow(){
        for (MemberInfo member : members) {
            System.out.println(member.timestamp + " " + member.port);
        }
    }

    /**
     * 返回字符串形式的组成员列表
     */
    public String members_toString(){
        StringBuilder sb = new StringBuilder();
        for (MemberInfo member:members){
            sb.append(member.timestamp).append(" ").append(member.port).append("\n");
        }
        return sb.toString();
    }

    /**
     * 添加组成员
     */
    public void memberAdd(String timestamp, Integer port){
        MemberInfo newmember=new MemberInfo(timestamp, port);
        for(int i=0;i<members.size();i++){
            if(members.get(i).port.equals(port)){
                members.get(i).timestamp=timestamp;//已存在则更新时间戳
                return;
            }
        }
        members.add(newmember);
    }

    /**
     * 移除组成员
     * port不存在也不会报错，不进行任何操作
     */
    public void memberRemove(Integer port){
        for(int i=0;i<members.size();i++){
            if(members.get(i).port.equals(port)){
                members.remove(i);
                break;
            }
        }
    }

    /**
     * 通过port找到下一个结点，也就是要check的服务器
     */
    public Integer findNextServer(Integer port){
        int flag=-1;
        for(int i=0;i<members.size();i++){
            if(members.get(i).port.equals(port)){
                // members.remove(i);
                if(i==members.size()-1){
                    flag=0;
                }
                else flag=i+1;
                break;
            }

        }

        return members.get(flag).port;
    }

    /**
     * 寻找上一个结点
     */
    public Integer findLastServer(Integer port){
        int flag=-1;
        for(int i=0;i<members.size();i++){
            if(members.get(i).port.equals(port)){
                if(i==0){
                    flag=members.size()-1;
                }
                else flag=i-1;
                break;
            }

        }

        return members.get(flag).port;
    }
    
}
