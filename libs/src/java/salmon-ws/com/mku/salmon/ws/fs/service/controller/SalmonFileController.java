package com.mku.salmon.ws.fs.service.controller;

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.salmon.SalmonFile;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SalmonFileController {
    @GetMapping("/list")
    public List<RealFileNode> list(String path) {
        ArrayList<RealFileNode> list = new ArrayList<>();
        String[] parts = path.split("/");
        for(String part : parts) {
            if(part.length() == 0)
                continue;

        }
        return "files";
    }

    @PostMapping("/create")
    public RealFileNode createFile(String path, boolean isDirectory) {
        return null;
    }

    @GetMapping("/info")
    public RealFileNode info(String path) {
        return new SalmonFile(new JavaFile("test.txt"));
    }

    @GetMapping("/get")
    public RealFileNode get(String path) {
        return new SalmonFile(new JavaFile("test.txt"));
    }

    @PostMapping("/copy")
    public RealFileNode copy(String path) {
        return null;
    }

    @PutMapping("/move")
    public RealFileNode move(String path) {
        return null;
    }

    @PutMapping("/rename")
    public RealFileNode rename(String path) {
        return null;
    }

    @DeleteMapping("/delete")
    public RealFileNode delete(String sourcePath) {
        return null;
    }

}
