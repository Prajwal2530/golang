package kafka

import (
	"context"
	"encoding/json"
	"log/slog"

	kafkalib "github.com/segmentio/kafka-go"
)

type Producer struct {
	writer *kafkalib.Writer
}

func NewProducer(broker string) *Producer {
	w := &kafkalib.Writer{
		Addr:     kafkalib.TCP(broker),
		Balancer: &kafkalib.LeastBytes{},
		AllowAutoTopicCreation: true,
		MaxAttempts: 3,
	}
	return &Producer{writer: w}
}

func (p *Producer) Produce(ctx context.Context, topic string, key string, message interface{}) error {
	msgBytes, _ := json.Marshal(message)

	err := p.writer.WriteMessages(ctx,
		kafkalib.Message{
			Topic: topic,
			Key:   []byte(key),
			Value: msgBytes,
		},
	)
	if err != nil {
		slog.Error("Failed to write to kafka", "topic", topic, "error", err)
	} else {
		slog.Info("Successfully pushed to kafka", "topic", topic)
	}
	return err
}

func (p *Producer) Close() {
	if p.writer != nil {
		p.writer.Close()
	}
}
