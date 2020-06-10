package com.xiaojukeji.kafka.manager.service.monitor;

import com.alibaba.fastjson.JSON;
import com.xiaojukeji.kafka.manager.common.entity.dto.alarm.AlarmNotifyDTO;
import com.xiaojukeji.kafka.manager.common.entity.dto.alarm.AlarmRuleDTO;
import com.xiaojukeji.kafka.manager.common.entity.dto.alarm.AlarmStrategyActionDTO;
import com.xiaojukeji.kafka.manager.service.notify.KafkaNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.security.Security;
import java.util.Date;
import java.util.Properties;

/**
 * 告警通知
 * @author zengqiao
 * @date 20/3/18
 */
@Component
public class AlarmNotifyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmNotifyService.class);

    @Value("${kafka-monitor.notify-kafka.cluster-id:}")
    private Long clusterId;

    @Value("${kafka-monitor.notify-kafka.topic-name:}")
    private String topicName;

    @Autowired
    private KafkaNotifier kafkaNotifier;

    /**
     * 发送者邮箱账号
     */
    @Value("${mail-smtp.username:}")
    private   String username;
    /**
     * 发送者邮箱密码
     */
    @Value("${mail-smtp.password:}")
    private   String password;
    /**
     * smtp 服务器地址
     */
    @Value("${mail-smtp.host:}")
    private   String smtpHost;
    /**
     * smtp 服务器端口
     */
    @Value("${mail-smtp.port:}")
    private   String smtpPort;

    public void send(AlarmRuleDTO alarmRuleDTO) {
        if (clusterId == null || StringUtils.isEmpty(topicName)) {
            LOGGER.error("application.yml monitor config illegal");
            return;
        }
        // todo 当前只有Kafka的方式，所以这里没有进行判断
        String messages= JSON.toJSONString(convert2AlarmNotifyDTO(alarmRuleDTO));
        kafkaNotifier.produce(clusterId, topicName, messages);

        AlarmNotifyService alarmNotifyService=new AlarmNotifyService();
        String message = AlarmNotifyService.builder().addHeader("你好:", 2)
                .newLine()
                .addMessage("&nbsp;&nbsp;&nbsp;&nbsp请关注预警信息: ")
                .newLine()
                .addMessage("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp告警集群： ").addMessage(alarmRuleDTO.getClusterId().toString()).newLine()
                .addMessage("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp告警名称： ").addMessage(alarmRuleDTO.getName()).newLine()
                .addMessage("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp告警规则： ").addMessage(JSON.toJSONString(alarmRuleDTO.getStrategyExpression())).newLine()
                .addMessage("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp过滤规则： ").addMessage(JSON.toJSONString(alarmRuleDTO.getStrategyFilterMap()))
                .build();
        alarmNotifyService.sendEmil(alarmRuleDTO.getMailbox().replaceAll("，",","),"梧桐车联智能商业系统预警",message);

    }

    private AlarmNotifyDTO convert2AlarmNotifyDTO(AlarmRuleDTO alarmRuleDTO) {
        AlarmNotifyDTO alarmNotifyDTO = new AlarmNotifyDTO();
        alarmNotifyDTO.setAlarmRuleId(alarmRuleDTO.getId());
        AlarmStrategyActionDTO alarmStrategyActionDTO = alarmRuleDTO.getStrategyActionMap().get("KAFKA");
        if (alarmStrategyActionDTO == null) {
            alarmNotifyDTO.setActionTag("");
        }
        alarmNotifyDTO.setMessage(JSON.toJSONString(alarmRuleDTO));
        return alarmNotifyDTO;
    }

    public   void sendEmil(String to,String subject, String message) {
        try {

            //启用ssl
            Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
            final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
            //设置邮件会话参数
            Properties props = new Properties();
            //邮箱的发送服务器地址
            props.setProperty("mail.smtp.host", smtpHost);
            props.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
            props.setProperty("mail.smtp.socketFactory.fallback", "false");
            //邮箱发送服务器端口,使用SSL，端口号465
            props.setProperty("mail.smtp.port", smtpPort);
            props.setProperty("mail.smtp.socketFactory.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            //获取到邮箱会话,利用匿名内部类的方式,将发送者邮箱用户名和密码授权给jvm
            Session session = Session.getDefaultInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
            session.setDebug(true);
            //通过会话,得到一个邮件,用于发送
            MimeMessage msg = new MimeMessage(session);

            //设置发件人
            msg.setFrom(new InternetAddress(username));
            //设置收件人,to为收件人,cc为抄送,bcc为密送
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
            /*msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(to, false));
            msg.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(to, false));*/
            msg.setSubject(subject);
            //设置文本邮件消息
            //msg.setText(message);
            //设置mime类型 邮件消息
            msg.setContent(message, "text/html;charset = UTF-8");
            //设置发送的日期
            msg.setSentDate(new Date());

            //调用Transport的send方法去发送邮件
            Transport.send(msg);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static MailMessageBuilder builder(){
        return new MailMessageBuilder();
    }


    public static class MailMessageBuilder {

        private StringBuilder sb;

        private MailMessageBuilder(){
            sb=new StringBuilder();
        }

        public MailMessageBuilder addMessage(String msg){
            sb.append(msg);
            return this;
        }

        /**
         * 添加标题
         */
        public MailMessageBuilder addHeader(String header,int level){
            String headerLabel="h"+level;
            sb.append("<").append(headerLabel).append(">");
            sb.append(header);
            sb.append("</").append(headerLabel).append(">");
            return this;
        }

        /**
         * 换行
         */
        public MailMessageBuilder newLine(){
            sb.append("<br/>");
            return this;
        }

        /**
         * 添加图片
         */
        public MailMessageBuilder addImgUrl(String imgUrl){
            sb.append("<img src=\"");
            sb.append("\" >");
            return this;
        }

        public String build(){
            return this.sb.toString();
        }
    }
}