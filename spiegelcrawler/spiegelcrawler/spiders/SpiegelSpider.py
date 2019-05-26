# -*- coding: utf-8 -*-
import scrapy

class SpiegelspiderSpider(scrapy.Spider):
    name = 'spiegelspider'

    def start_requests(self):
        self.current_articles = 0
        self.max_articles = int(getattr(self, 'max_a', 100))
        urls = [
            'https://www.spiegel.de/politik/',
            'https://www.spiegel.de/thema/meinung/',
            'https://www.spiegel.de/wirtschaft/',
            'https://www.spiegel.de/panorama/',
            'https://www.spiegel.de/sport/',
            'https://www.spiegel.de/kultur/',
            'https://www.spiegel.de/netzwelt/',
            'https://www.spiegel.de/wissenschaft/',
            'https://www.spiegel.de/gesundheit/',
            'https://www.spiegel.de/reise/'
        ]

        for url in urls:
            yield scrapy.Request(url=url, callback=self.parse)

    def parse(self, response):
        for article in response.css('.article-title a'):
            if self.current_articles < self.max_articles:
                self.current_articles+=1
                yield response.follow(article, self.parse_tags)

        if self.current_articles < self.max_articles:
            next_page = response.css('.archive-link-box .link-right').attrib['href']
            if next_page is not None:
                yield response.follow(next_page, self.parse)
        else:
            self.current_articles = 0

    def parse_tags(self, response):
        yield {
            'title': response.css('meta[property="og:title"]').attrib['content'],
            'tags': response.css('meta[name="news_keywords"]').attrib['content']
        }
