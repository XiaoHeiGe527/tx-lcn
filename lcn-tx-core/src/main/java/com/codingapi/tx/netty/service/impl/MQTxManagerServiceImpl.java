package com.codingapi.tx.netty.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.codingapi.tx.Constants;
import com.codingapi.tx.aop.bean.TxTransactionInfo;
import com.codingapi.tx.compensate.model.CompensateInfo;
import com.codingapi.tx.compensate.service.CompensateService;
import com.codingapi.tx.config.ConfigReader;
import com.codingapi.tx.framework.utils.SerializerUtils;
import com.codingapi.tx.framework.utils.SocketManager;
import com.codingapi.tx.listener.model.Request;
import com.codingapi.tx.listener.model.TxGroup;
import com.codingapi.tx.listener.service.ModelNameService;
import com.codingapi.tx.netty.service.MQTxManagerService;
import com.lorne.core.framework.utils.encode.Base64Utils;
import com.lorne.core.framework.utils.http.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by lorne on 2017/6/30.
 */
@Service
public class MQTxManagerServiceImpl implements MQTxManagerService {


    @Autowired
    private ModelNameService modelNameService;

    @Autowired
    private ConfigReader configReader;

    @Autowired
    private CompensateService compensateService;



    @Override
    public TxGroup createTransactionGroup() {
        JSONObject jsonObject = new JSONObject();
        Request request = new Request("cg", jsonObject.toString());
        String json = SocketManager.getInstance().sendMsg(request);
        return TxGroup.parser(json);
    }

    @Override
    public TxGroup addTransactionGroup(String groupId, String taskId, boolean isGroup, String methodStr) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("t", taskId);
        jsonObject.put("u", Constants.uniqueKey);
        jsonObject.put("ms", methodStr);
        jsonObject.put("ip", modelNameService.getIpAddress());
        jsonObject.put("mn", modelNameService.getModelName());
        jsonObject.put("s", isGroup ? 1 : 0);
        Request request = new Request("atg", jsonObject.toString());
        String json =  SocketManager.getInstance().sendMsg(request);
        return TxGroup.parser(json);
    }


    @Override
    public int closeTransactionGroup(final String groupId, final int state) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("s", state);
        Request request = new Request("ctg", jsonObject.toString());
        String json =  SocketManager.getInstance().sendMsg(request);
        try {
            return Integer.parseInt(json);
        }catch (Exception e){
            return 0;
        }
    }


    @Override
    public void uploadModelInfo() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("m", modelNameService.getModelName());
        jsonObject.put("i", modelNameService.getIpAddress());
        jsonObject.put("u", modelNameService.getUniqueKey());
        Request request = new Request("umi", jsonObject.toString());
        String json = SocketManager.getInstance().sendMsg(request);
        System.out.println(json);
    }

    @Override
    public int checkTransactionInfo(String groupId, String taskId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("t", taskId);
        Request request = new Request("ckg", jsonObject.toString());
        String json =  SocketManager.getInstance().sendMsg(request);
        try {
            return Integer.parseInt(json);
        }catch (Exception e){
            return -2;
        }
    }


    @Override
    public int getTransaction(String groupId, String waitTaskId) {
        String json = HttpUtils.get(configReader.getTxUrl() + "getTransaction?groupId=" + groupId + "&taskId=" + waitTaskId);
        if (json == null) {
            return -2;
        }
        json = json.trim();
        try {
            return Integer.parseInt(json);
        }catch (Exception e){
            return -2;
        }
    }


    @Override
    public int clearTransaction(String groupId, String waitTaskId, boolean isGroup) {
        String murl = configReader.getTxUrl() + "clearTransaction?groupId=" + groupId + "&taskId=" + waitTaskId + "&isGroup=" + (isGroup ? 1 : 0);
        String clearRes = HttpUtils.get(murl);
        if(clearRes==null){
            return -1;
        }
        return  clearRes.contains("true") ? 1 : 0;
    }


    @Override
    public String httpGetServer() {
        String murl = configReader.getTxUrl() + "getServer";
        return HttpUtils.get(murl);
    }

    @Override
    public void sendCompensateMsg(String groupId, long time, TxTransactionInfo info) {

        String modelName = modelNameService.getModelName();
        String uniqueKey = modelNameService.getUniqueKey();
        String address = modelNameService.getIpAddress();


        byte[] serializers =  SerializerUtils.serializeTransactionInvocation(info.getInvocation());
        String data = Base64Utils.encode(serializers);

        String className = info.getInvocation().getTargetClazz().getName();
        String methodStr = info.getInvocation().getMethodStr();
        long currentTime = System.currentTimeMillis();


        CompensateInfo compensateInfo = new CompensateInfo(currentTime, modelName, uniqueKey, data, methodStr, className, groupId, address, time);

        String json = HttpUtils.post(configReader.getTxUrl() + "sendCompensateMsg", compensateInfo.toParamsString());

        compensateInfo.setResJson(json);

        //记录本地日志
        compensateService.saveLocal(compensateInfo);

    }
}