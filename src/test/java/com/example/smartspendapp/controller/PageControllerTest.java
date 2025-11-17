package com.example.smartspendapp.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PageController using standalone MockMvc (no Spring context).
 */
class PageControllerTest {

    private MockMvc mockMvc;
    private PageController pageController;

    @BeforeEach
    void setUp() {
        pageController = new PageController();

        // Add a simple view resolver so view names like "header" won't dispatch back to controller
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        // prefix/suffix can be anything valid for your project; using empty prefix so the resolved view is /header.html
        viewResolver.setPrefix("");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders
                .standaloneSetup(pageController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void headerPage_returnsHeaderViewAndModel() throws Exception {
        mockMvc.perform(get("/header"))
                .andExpect(status().isOk())
                .andExpect(view().name("header"))
                .andExpect(model().attributeExists("name"))
                .andExpect(model().attribute("name", is("tamil")));
    }

    @Test
    void root_redirectsToDashboard() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }
}

