import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // 方式1：从命令行参数获取文件路径
        if (args.length == 0) {
            System.err.println("用法: java Main <文件路径>");
            System.exit(1);
        }
        
        String filePath = args[0];
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.err.println("错误: 无法读取文件 " + filePath);
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}