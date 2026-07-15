import java.io.*;
import java.util.*;
import redis.clients.jedis.Jedis;

public class CitySeeder {
    public static void main(String[] args) throws Exception {
        Jedis jedis = new Jedis("bpa-redis", 6379);
        
        // serialize key the same way Spring does
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject("generic-city-pref");
        oos.flush();
        byte[] keyBytes = bos.toByteArray();
        
        // serialize value map
        Map<String,Object> cityMap = new HashMap<>();
        cityMap.put("cityCode", "AMR");
        cityMap.put("citymunicipalityname", "Amritsar");
        cityMap.put("cityGrade", "G");
        cityMap.put("domainurl", "localhost");
        cityMap.put("tenantid", "pb.amritsar");
        cityMap.put("citylogo", "none");
        cityMap.put("cityname", "Amritsar");
        
        for (Map.Entry<String,Object> e : cityMap.entrySet()) {
            ByteArrayOutputStream kbos = new ByteArrayOutputStream();
            ObjectOutputStream koos = new ObjectOutputStream(kbos);
            koos.writeObject(e.getKey());
            koos.flush();
            
            ByteArrayOutputStream vbos = new ByteArrayOutputStream();
            ObjectOutputStream voos = new ObjectOutputStream(vbos);
            voos.writeObject(e.getValue());
            voos.flush();
            
            jedis.hset(keyBytes, kbos.toByteArray(), vbos.toByteArray());
        }
        System.out.println("Seeded generic-city-pref OK");
        jedis.close();
    }
}
