import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;

public class EmailVarification {

    private static final Integer SUCCESS = 250;
    private static final Integer READY = 220;
    private static final Integer SMTP_PORT = 25;
    private static final Integer TIME_OUT = 15 * 1000;
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    /**
     * SMPT 코드를 응답 받는다.
     *
     * @param bufferedReader bufferedReader
     * @return res SMTP 응답
     */
    private static int hear(BufferedReader bufferedReader) throws IOException {
        String line;
        int res = 0;

        while ((line = bufferedReader.readLine()) != null) {
            if (line.charAt(3) != '-') {
                String prefix = line.substring(0, 3);
                return Integer.parseInt(prefix);
            }
        }
        return res;
    }

    /**
     * SMPT 코드를 요청한다.
     *
     * @param bufferedWriter bufferedWriter
     * @param text           요청할 텍스트
     */
    private static void say(BufferedWriter bufferedWriter, String text) throws IOException {
        bufferedWriter.write(text + "\r\n");
        bufferedWriter.flush();
    }

    /**
     * MX 레코드를 조회한다.
     *
     * @param hostName 조회할 host
     * @return ArrayList<String> MX 레코드 목록
     */
    private static ArrayList<String> getMX(String hostName) throws NamingException {

        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        DirContext ictx = new InitialDirContext(env);
        Attributes attrs = ictx.getAttributes(hostName, new String[]{"MX"});
        Attribute attr = attrs.get("MX");

        if ((attr == null) || (attr.size() == 0)) {
            attrs = ictx.getAttributes(hostName, new String[]{"A"});
            attr = attrs.get("A");
            if (attr == null) {
                throw new NamingException("No match for name '" + hostName + "'");
            }
        }

        ArrayList<String> res = new ArrayList<>();
        NamingEnumeration<?> en = attr.getAll();

        while (en.hasMore()) {
            String mailhost;
            String x = (String) en.next();
            String[] f = x.split(" ");
            // 도메인으로 되어있지 않은 경우
            if (f.length == 1) {
                mailhost = f[0];
                // 도메인에 .이 포함되어 있을 경우
            } else if (f[1].endsWith(".")) {
                mailhost = f[1].substring(0, (f[1].length() - 1));
                // 정상 도메인의 경우
            } else {
                mailhost = f[1];
            }
            res.add(mailhost);
        }
        return res;
    }

    private static Map<String, Object> returnResult(Boolean valid, String message) {

        Map<String, Object> result = new HashMap<>();

        if (valid) {
            result.put("resultCode", "Email Verification Success.");
        } else {
            result.put("resultCode", "Email Verification Failed.");
            result.put("failMessage", message);
        }

        return result;
    }

    /**
     * 위 메소드를 통한 핵심 Email Validation
     *
     * @param address 조회할 이메일 주소
     * @return Boolean 이메일 주소 실제 유효성 여부
     */
    public static Map<String, Object> isAddressValid(String address) {

        if (!address.matches(EMAIL_REGEX)) {
            return returnResult(false, "It's not in email format.");
        }

        String domain = address.substring(address.indexOf('@') + 1);
        ArrayList<String> mxList;
        try {
            mxList = getMX(domain);
        } catch (NamingException ex) {
            return returnResult(false, "This is not a formally registered email domain.");
        }

        if (mxList.size() == 0) {
            return returnResult(false, "This is not a formally registered email domain.");
        }

        for (String o : mxList) {
            SocketAddress address2 = new InetSocketAddress(o, SMTP_PORT);
            try (Socket skt = new Socket()) {
                skt.setSoTimeout(TIME_OUT);
                skt.connect(address2, TIME_OUT);
                int res;
                try (BufferedReader rdr = new BufferedReader(new InputStreamReader(skt.getInputStream()));
                     BufferedWriter wtr = new BufferedWriter(new OutputStreamWriter(skt.getOutputStream()))) {
                    res = hear(rdr);
                    if (res != READY) {
                        return returnResult(false, "Invalid header.");
                    }
                    say(wtr, "EHLO github.com");

                    res = hear(rdr);
                    if (res != SUCCESS) {
                        return returnResult(false, "Not ESMTP.");
                    }

                    say(wtr, "MAIL FROM: <test@naver.com>");
                    res = hear(rdr);
                    if (res != SUCCESS) {
                        return returnResult(false, "Sender rejected");
                    }

                    say(wtr, "RCPT TO: <" + address + ">");
                    res = hear(rdr);

                    //                    say(wtr, "RSET");
                    //                    hear(rdr);
                    say(wtr, "QUIT");
                    hear(rdr);

                    if (res != SUCCESS) {
                        return returnResult(false, "Address is not valid");
                    }
                } catch (Exception ex) {
                    return returnResult(false, "TCP Send, Receive Error occurred." + ex);
                }

            } catch (Exception ex) {
                return returnResult(false, "Error occurred.");
            }
        }
        return returnResult(true, "");
    }

    public static void main(String[] args) {

        String testData = "test1@naver.com";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        System.out.println(timestamp);
        Map<String, Object> result = isAddressValid(testData);
        Timestamp timestamp2 = new Timestamp(System.currentTimeMillis());
        System.out.println(timestamp2);

        System.out.println(result.get("resultCode"));
        if (result.get("failMessage") != null) {
            System.out.println(result.get("failMessage"));
            System.out.println("Email Address is " + testData);
        }

    }
}