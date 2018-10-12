package com.shuo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by shuoGG on 2018/10/11
 */
@Controller
@RequestMapping("test")
@ResponseBody
public class TestController {

    @RequestMapping("play")
    public String play() {
        return "OK";
    }
}
