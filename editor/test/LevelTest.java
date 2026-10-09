import com.sky.totmeditor.Level;
import java.nio.file.*; import java.util.*; import java.io.*;
public class LevelTest {
  public static void main(String[] a) throws Exception {
    byte[] data = Files.readAllBytes(Paths.get(a[0]));
    List<Level> st = Level.parseStages(data);
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    int invalid=0, textOk=0; Map<String,Integer> errs=new TreeMap<>();
    for (Level l : st) {
      bo.write(l.toGameBinary());
      Level r = Level.fromText("x", l.toText());
      if (Arrays.equals(r.toGameBinary(), l.toGameBinary()) && r.name.equals(l.name)) textOk++;
      List<String> e = l.validate(); if(!e.isEmpty()){invalid++; for(String s:e) errs.merge(s.replaceAll("\\(.*\\)",""),1,Integer::sum);}
    }
    System.out.println("stages: "+st.size()+" binaire identique: "+Arrays.equals(bo.toByteArray(), data)+" texte aller-retour: "+textOk);
    System.out.println("officiels invalides selon validate(): "+invalid+" "+errs);
    // fichier produit par totm_levels.py
    Level py = Level.fromText("py", new String(Files.readAllBytes(Paths.get(a[1])),"UTF-8"));
    System.out.println("py stage_001 == binaire: "+Arrays.equals(py.toGameBinary(), st.get(0).toGameBinary()));
    // édition
    Level s = Level.starter("t"); System.out.println("starter valide: "+s.validate());
    s.set(5,5,Level.ENTER); System.out.println("départ unique après déplacement: "+s.count(Level.ENTER)+" "+s.get(5,5));
    s.resize(25,40); System.out.println("resize: "+s.width+"x"+s.height+" départ conservé="+s.count(Level.ENTER)+" bord="+s.validate());
    s.resize(10,10); System.out.println("réduction: "+s.validate());
  }
}
